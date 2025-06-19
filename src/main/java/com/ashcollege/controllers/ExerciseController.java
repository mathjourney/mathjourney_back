package com.ashcollege.controllers;

import com.ashcollege.entities.UserEntity;
import com.ashcollege.service.ExerciseService;
import com.ashcollege.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api/exercises")
public class ExerciseController {

    private final UserService userService;
    private final ExerciseService exerciseService;

    @Autowired
    public ExerciseController(UserService userService,
                              ExerciseService exerciseService) {
        this.userService = userService;
        this.exerciseService = exerciseService;
    }

    /* ---------- ‎/next‏ ---------- */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/next")
    public ResponseEntity<Map<String, Object>> getNextQuestion(@RequestParam int topicId) {
        UserEntity user = userService.getCurrentUser();
        if (user == null)
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));

        try {
            Map<String, Object> q = exerciseService.generateQuestion(topicId);
            return ResponseEntity.ok(q);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "אירעה שגיאה ביצירת השאלה", "details", e.getMessage()));
        }
    }

    /* ---------- ‎/answer‏ ---------- */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/answer")
    public ResponseEntity<Map<String, Object>> checkAnswer(@RequestBody Map<String, Object> body) {

        UserEntity user = userService.getCurrentUser();
        if (user == null)
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));

        @SuppressWarnings("unchecked")
        Map<String, Object> q = (Map<String, Object>) body.get("question");
        if (q == null)
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "חסרה שאלה בבקשה"));

        Integer userAns = (Integer) body.get("answer");
        if (userAns == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Missing answer"));

        boolean correct = exerciseService.checkAnswer(q, userAns);
        int topicId = (int) q.get("topicId");

        /* ספירת ניסיונות/שגיאות */
        userService.incrementTotalExercises(user.getId());
        exerciseService.incrementAttempt(user.getId(), topicId);

        String levelUpMsg = exerciseService.updateStreaksAndLevel(user.getId(), topicId, correct);

        if (!correct) {
            userService.incrementTotalMistakes(user.getId());
            exerciseService.incrementTopicMistakes(user.getId(), topicId); // ✅ הוסיפי את זה

        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("isCorrect", correct);
        resp.put("correctAnswer", q.get("correctAnswer"));
        resp.put("currentLevel",
                exerciseService.getUserTopicLevel(user.getId(), topicId));
        if (levelUpMsg != null)
            resp.put("levelChangeMessage", levelUpMsg);
        return ResponseEntity.ok(resp);
    }

    /* ---------- ‎/next-random‏ ---------- */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/next-random")
    public ResponseEntity<Map<String, Object>> getRandom() {
        UserEntity user = userService.getCurrentUser();
        if (user == null)
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));

        int topic = new Random().nextInt(8) + 1;
        Map<String, Object> q = exerciseService.generateQuestion(topic);
        return ResponseEntity.ok(q);
    }
}
