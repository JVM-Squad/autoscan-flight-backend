package com.webapp.flightsearch.controller;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import com.webapp.flightsearch.dto.BookmarkDto;
import com.webapp.flightsearch.dto.LoginDto;
import com.webapp.flightsearch.dto.SignUpDto;
import com.webapp.flightsearch.entity.FlightBookmark;
import com.webapp.flightsearch.entity.User;
import com.webapp.flightsearch.repository.UserRepository;
import com.webapp.flightsearch.service.UserDetail;
import com.webapp.flightsearch.util.FirestoreRetriever;
import com.webapp.flightsearch.util.FirestoreWriter;
import com.webapp.flightsearch.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(maxAge = 3600)
@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserDetail userDetail;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<String> authenticateUser(@RequestBody LoginDto loginDto) {
        try {
            Firestore firestore = FirestoreClient.getFirestore();
            FirestoreRetriever retreiver = new FirestoreRetriever(firestore);
            LoginDto user = retreiver.getUserFromFirestore(firestore, loginDto.getUsername());
            Authentication authentication = authenticationManager
                    .authenticate(
                            new UsernamePasswordAuthenticationToken(loginDto.getUsername(), loginDto.getPassword()));

            System.out.println(user);
            // // validate via firebase
            // Authentication authenticationFirebase = authenticationManager
            // .authenticate(
            // new UsernamePasswordAuthenticationToken(user.getUsername(),
            // user.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String token = JwtUtil.generateJwtToken(authentication);
            return new ResponseEntity<>(token, HttpStatus.OK);
        } catch (AuthenticationException e) {
            return new ResponseEntity<>("Login failed: Invalid username or password.", HttpStatus.UNAUTHORIZED);
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@RequestBody SignUpDto signUpDto) throws FileNotFoundException {
        // checking for username exists in a database
        if (userRepository.existsByUserName(signUpDto.getUsername())) {
            return new ResponseEntity<>("Username is already exist!", HttpStatus.BAD_REQUEST);
        }
        // checking for email exists in a database
        if (userRepository.existsByEmail(signUpDto.getEmail())) {
            return new ResponseEntity<>("Email is already exist!", HttpStatus.BAD_REQUEST);
        }
        User user = userDetail.createUser(signUpDto);

        Firestore firestore = FirestoreClient.getFirestore(); // Obtain Firestore
        FirestoreWriter userWriter = new FirestoreWriter();
        userWriter.saveUserToFirestore(firestore, user);

        return ResponseEntity.ok("User is registered successfully! " + user);
    }

    @GetMapping("/userProfile/{userName}")
    public ResponseEntity<?> getUserProfile(@PathVariable String userName) {
        User user = userRepository.findByUserName(userName);
        return new ResponseEntity<>(user, HttpStatus.OK);
    }

    @PutMapping("/editProfile/{userName}")
    public ResponseEntity<?> editProfile(@PathVariable String userName,
            @RequestBody User userDetails) {

        User user = userRepository.findByUserName(userName);
        System.out.println(userDetails);
        if (user != null) {
            if (userDetails.getEmail() != null) {
                user.setEmail(userDetails.getEmail());
            }
            if (userDetails.getUserName() != null) {
                user.setUserName(userDetails.getUserName());
            }
            if (userDetails.getName() != null) {
                user.setName(userDetails.getName());
            }
            User updatedUser = userRepository.save(user);

            Firestore firestore = FirestoreClient.getFirestore(); // Obtain Firestore
            FirestoreWriter userWriter = new FirestoreWriter();
            userWriter.saveUserToFirestore(firestore, updatedUser);
            return ResponseEntity.ok(updatedUser);
        } else {
            // Handle the case where user is null
            System.out.println(userDetails);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(userDetails);
        }

    }

    @PutMapping("/change-password/{userName}")
    public ResponseEntity<?> changePassword(@PathVariable String userName, @RequestBody User passwords) {
        User user = userRepository.findByUserName(userName);
        System.out.println(user);
        System.out.println(passwords);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }
        boolean matches = passwordEncoder.matches(passwords.getOldPassword(), user.getPassword());
        if (!matches) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Old password does not match.");
        }
        user.setPassword(passwordEncoder.encode(passwords.getNewPassword()));
        userRepository.save(user);
        Firestore firestore = FirestoreClient.getFirestore(); // Obtain Firestore
        FirestoreWriter userWriter = new FirestoreWriter();
        userWriter.saveUserToFirestore(firestore, user);
        return ResponseEntity.ok("Password changed successfully.");
    }

    @PostMapping("/{userName}/bookmark")
    public ResponseEntity<Map<String, Object>> bookmarkFlight(@PathVariable String userName,
            @RequestBody BookmarkDto savedBookmark) {
        System.out.println("Received userName: " + userName);
        System.out.println("Received bookmark details: " + savedBookmark);
        FlightBookmark flightBookmark = userDetail.bookmarkFlight(userName, savedBookmark);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Bookmark added successfully!");
        response.put("bookmark", flightBookmark);

        Firestore firestore = FirestoreClient.getFirestore();
        FirestoreWriter bookmarkWriter = new FirestoreWriter();
        bookmarkWriter.saveBookMarkToFirestore(firestore, flightBookmark, savedBookmark);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userName}/bookmarks")
    public ResponseEntity<List<BookmarkDto>> getBookmarks(@PathVariable String userName) {

        Firestore firestore = FirestoreClient.getFirestore();
        FirestoreRetriever retriever = new FirestoreRetriever(firestore);
        List<BookmarkDto> bookmarks = retriever.getBookmarks(userName);
        System.out.println(bookmarks);
        return ResponseEntity.ok(bookmarks);
    }
}
