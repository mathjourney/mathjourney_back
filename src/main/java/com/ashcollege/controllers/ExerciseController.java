package com.ashcollege.controllers;

import com.ashcollege.entities.UserEntity;
import com.ashcollege.service.ExerciseService;
import com.ashcollege.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/exercises")
public class ExerciseController {

    private final UserService    userService;
    private final ExerciseService exerciseService;

    @Autowired
    public ExerciseController(UserService userService,
                              ExerciseService exerciseService) {
        this.userService   = userService;
        this.exerciseService = exerciseService;
    }

    /* ---------- ‎/next ---------- */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/next")
    public ResponseEntity<?> getNextQuestion(@RequestParam("topicId") Integer topicId) {

        if (topicId == null || topicId <= 0)
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "topicId לא תקין – חייב להיות מספר חיובי"));

        UserEntity user = userService.getCurrentUser();
        if (user == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "User not authenticated"));

        try {
            Map<String, Object> q = exerciseService.generateQuestion(topicId);
            return ResponseEntity.ok(q);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "message", "אירעה שגיאה ביצירת השאלה",
                            "details", e.getMessage()
                    ));
        }
    }

    /* ---------- ‎/answer ---------- */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/answer")
    public ResponseEntity<?> checkAnswer(@RequestBody Map<String, Object> body) {

        UserEntity user = userService.getCurrentUser();
        if (user == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "User not authenticated"));

        /* ───── חילוץ פרמטרים בצורה בטוחה ───── */
        Object questionObj = body.get("question");
        Object answerObj   = body.get("answer");

        if (!(questionObj instanceof Map<?,?>))
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Missing or malformed 'question'"));

        if (!(answerObj instanceof Number))
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Missing or malformed 'answer'"));

        @SuppressWarnings("unchecked")
        Map<String, Object> q = (Map<String, Object>) questionObj;
        int userAnswer = ((Number) answerObj).intValue();

        /* topicId – חובה בשביל עדכון רמות */
        int topicId = ((Number) Optional.ofNullable(q.get("topicId"))
                .orElse(0)).intValue();

        boolean correct = exerciseService.checkAnswer(q, userAnswer);

        /* Book-keeping */
        userService.incrementTotalExercises(user.getId());
        exerciseService.incrementAttempt(user.getId(), topicId);
        if (!correct) {
            userService.incrementTotalMistakes(user.getId());
            exerciseService.incrementTopicMistakes(user.getId(), topicId);
        }

        String levelMsg = exerciseService.updateStreaksAndLevel(
                user.getId(), topicId, correct);

        Map<String, Object> resp = new HashMap<>();
        resp.put("isCorrect",      correct);
        resp.put("correctAnswer",  q.get("correctAnswer"));
        resp.put("currentLevel",   exerciseService.getUserTopicLevel(user.getId(), topicId));
        if (levelMsg != null)      resp.put("levelChangeMessage", levelMsg);

        return ResponseEntity.ok(resp);
    }

    /* ---------- ‎/next-random ---------- */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/next-random")
    public ResponseEntity<?> getRandom() {
        UserEntity user = userService.getCurrentUser();
        if (user == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "User not authenticated"));

        int topic = new Random().nextInt(8) + 1;
        Map<String, Object> q = exerciseService.generateQuestion(topic);
        return ResponseEntity.ok(q);
    }
}
