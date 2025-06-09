package com.ashcollege.service;

import com.ashcollege.entities.UserEntity;
import com.ashcollege.entities.UserTopicLevelEntity;
import com.ashcollege.repository.UserRepository;
import com.ashcollege.repository.UserTopicLevelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ExerciseService {

    private static final Logger logger = LoggerFactory.getLogger(ExerciseService.class);

    @Autowired private UserService userService;
    @Autowired private UserTopicLevelRepository userTopicLevelRepo;
    @Autowired private UserRepository userRepo;

    private final Random rand = new Random();

    public Map<String, Object> generateQuestion(int topicId) {
        UserEntity user = userService.getCurrentUser();
        if (user == null) throw new RuntimeException("No current user found");

        UserTopicLevelEntity ute = userTopicLevelRepo.findByUserIdAndTopicId(user.getId(), topicId);
        if (ute == null) {
            ute = new UserTopicLevelEntity();
            ute.setUserId(user.getId());
            ute.setTopicId(topicId);
            ute.setLevel(1);
            ute.setMistakes(0);
            ute.setAttempts(0);
            userTopicLevelRepo.save(ute);
        }

        int currentLevel = Math.max(1, ute.getLevel());
        logger.info("Topic {} ⇒ CurrentLevel={}", topicId, currentLevel);

        Map<String, Object> question;
        switch (topicId) {
            case 1  -> question = generateBasicArithmetic("+", currentLevel);
            case 2  -> question = generateBasicArithmetic("-", currentLevel);
            case 3  -> question = generateBasicArithmetic("×", currentLevel);
            case 4  -> question = generateBasicArithmetic("÷", currentLevel);
            case 5  -> question = generateFractionQuestion("+", currentLevel);
            case 6  -> question = generateFractionQuestion("-", currentLevel);
            case 7  -> question = generateFractionQuestion("×", currentLevel);
            case 8  -> question = generateFractionQuestion("÷", currentLevel);
            default -> question = generateBasicArithmetic("+", currentLevel);
        }

        question.put("topicId", topicId);
        return question;
    }

    public void increaseUserTopicLevel(int userId, int topicId) {
        UserTopicLevelEntity rec = userTopicLevelRepo.findByUserIdAndTopicId(userId, topicId);
        if (rec != null) {
            int old = rec.getLevel();
            rec.setLevel(old + 1);
            userTopicLevelRepo.save(rec);
            logger.info("User {} topic {} ⇒ level {} → {}", userId, topicId, old, rec.getLevel());
            updateGeneralLevel(userId);
        }
    }

    public boolean checkAnswer(Map<String, Object> q, int userAnswer) {
        return userAnswer == (int) q.get("correctAnswer");
    }

    public void incrementTopicMistakes(int userId, int topicId) {
        UserTopicLevelEntity rec = userTopicLevelRepo.findByUserIdAndTopicId(userId, topicId);
        if (rec != null) {
            rec.setMistakes(rec.getMistakes() + 1);
            userTopicLevelRepo.save(rec);
        }
    }

    public void incrementAttempt(int userId, int topicId) {
        UserTopicLevelEntity rec = userTopicLevelRepo.findByUserIdAndTopicId(userId, topicId);
        if (rec != null) {
            rec.setAttempts(rec.getAttempts() + 1);
            userTopicLevelRepo.save(rec);
        }
    }

    public int getUserTopicLevel(int userId, int topicId) {
        UserTopicLevelEntity rec = userTopicLevelRepo.findByUserIdAndTopicId(userId, topicId);
        return rec == null ? 1 : Math.max(1, rec.getLevel());
    }

    public void updateGeneralLevel(int userId) {
        List<UserTopicLevelEntity> levels = userTopicLevelRepo.findByUserId(userId);
        if (levels.isEmpty()) return;

        int min = levels.stream()
                .filter(l -> l.getLevel() > 0)
                .mapToInt(UserTopicLevelEntity::getLevel)
                .min()
                .orElse(1);

        userRepo.findById(userId).ifPresent(u -> {
            u.setLevel(min);
            userRepo.save(u);
        });
    }

    private Map<String, Object> generateBasicArithmetic(String sign, int level) {
        level = Math.max(1, level);

        int a = 0, b = 0, correct = 0;
        boolean valid = false;
        int attempts = 0;

        if (sign.equals("÷")) {
            int maxA      = level <= 5 ? level * 10 : 50 + (level - 5) * 50;
            int maxResult = level <= 5 ? 4 + level  : 10 + (level - 5) * 2;
            int minA      = Math.max(4, maxA / 5);

            while (!valid && attempts++ < 100) {
                a = rand.nextInt(maxA - minA + 1) + minA;

                List<Integer> divisors = new ArrayList<>();
                for (int i = 2; i <= a / 2; i++) {
                    if (a % i == 0 && a / i <= maxResult) divisors.add(i);
                }
                if (!divisors.isEmpty()) {
                    b = divisors.get(rand.nextInt(divisors.size()));
                    correct = a / b;
                    valid = true;
                }
            }

        } else {
            while (!valid && attempts++ < 100) {
                if (sign.equals("-")) {
                    int minDiff   = switch (level) {
                        case 1, 2 -> 3;
                        case 3, 4 -> 6;
                        case 5, 6 -> 10;
                        default    -> 20;
                    };
                    int maxValSub = level * 10;
                    int minValSub = Math.max(2, level * 2);

                    int range = (maxValSub / 2) - minValSub + 1;
                    if (range <= 0) range = 1;

                    b = rand.nextInt(range) + minValSub;
                    a = b + minDiff + rand.nextInt(maxValSub / 2);
                    correct = a - b;
                    if (correct >= 0) valid = true;

                } else {
                    int minVal = (level - 1) * 5;
                    int maxVal = level * 5;
                    a = rand.nextInt(maxVal - minVal + 1) + minVal;
                    b = rand.nextInt(maxVal - minVal + 1) + minVal;
                    correct = switch (sign) {
                        case "+" -> a + b;
                        case "×" -> a * b;
                        default  -> 0;
                    };
                    valid = true;
                }
            }
        }

        if (!valid) {
            throw new RuntimeException(
                    "Failed to create question after 100 tries (" + sign + ", level " + level + ')');
        }

        int[] answers = generateUniqueAnswers(correct);
        if (Arrays.stream(answers).allMatch(x -> x < 0))
            return generateBasicArithmetic(sign, level);

        Map<String, Object> q = new HashMap<>();
        q.put("first",  Math.max(a, b));
        q.put("second", Math.min(a, b));
        q.put("operationSign", sign);
        q.put("correctAnswer", correct);
        q.put("answers", answers);
        return q;
    }

    private int[] generateUniqueAnswers(int correct) {
        Set<Integer> set = new HashSet<>();
        set.add(correct);
        int tries = 0;
        while (set.size() < 4 && tries++ < 100) {
            int c = correct + rand.nextInt(11) - 5;
            if (c >= 0) set.add(c);
        }
        while (set.size() < 4) set.add(rand.nextInt(20));
        int[] arr = set.stream().mapToInt(Integer::intValue).toArray();
        shuffle(arr);
        return arr;
    }

    private void shuffle(int[] arr) {
        for (int i = arr.length - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            int tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }
    }

    private Map<String, Object> generateFractionQuestion(String sign, int level) {
        level = Math.max(1, level);
        int[] frac = createFractionPair(level);
        int a = frac[0], b = frac[1], c = frac[2], d = frac[3];

        if (sign.equals("-") && (a * d < c * b)) return generateFractionQuestion(sign, level);

        int num = 0, den = 0;
        switch (sign) {
            case "+" -> {
                num = a * d + b * c;
                den = b * d;
            }
            case "-" -> {
                num = a * d - b * c;
                den = b * d;
            }
            case "×" -> {
                num = a * c;
                den = b * d;
            }
            case "÷" -> {
                num = a * d;
                den = b * c;
            }
        }

        if (num < 0 || den <= 0) return generateFractionQuestion(sign, level);

        List<Integer> answersList = new ArrayList<>();
        Set<String> displayStrings = new HashSet<>();
        int correctEncoded = num * 1000 + den;
        String correctDisplay = fractionDisplay(num, den);

        answersList.add(correctEncoded);
        displayStrings.add(correctDisplay);

        int attempts = 0;
        while (answersList.size() < 4 && attempts++ < 100) {
            int offsetNum = rand.nextInt(5) - 2;
            int offsetDen = rand.nextInt(3);
            int newNum = Math.max(0, num + offsetNum);
            int newDen = Math.max(1, den + offsetDen);
            String candidateDisplay = fractionDisplay(newNum, newDen);
            if (!displayStrings.contains(candidateDisplay)) {
                answersList.add(newNum * 1000 + newDen);
                displayStrings.add(candidateDisplay);
            }
        }

        int[] answers = answersList.stream().mapToInt(Integer::intValue).toArray();
        shuffle(answers);

        Map<String, Object> q = new HashMap<>();
        q.put("first", a + "/" + b);
        q.put("second", c + "/" + d);
        q.put("operationSign", sign);
        q.put("correctAnswer", correctEncoded);
        q.put("answers", answers);
        return q;
    }

    private int[] createFractionPair(int level) {
        if (level < 1) level = 1;

        boolean sameDen = false, differDen = false;
        int maxDen = 5, forcedNum = -1;

        if (level == 1) { sameDen = true; maxDen = 5; }
        else if (level == 2) { sameDen = true; maxDen = 10; }
        else if (level == 3) { sameDen = true; maxDen = 20; }
        else if (level == 4) { differDen = true; maxDen = 5; forcedNum = 1; }
        else if (level == 5) { differDen = true; maxDen = 10; forcedNum = 1; }
        else { differDen = true; maxDen = Math.max(5, 5 * (level - 5)); forcedNum = 0; }

        if (sameDen) {
            int den = rand.nextInt(maxDen - 1) + 2;
            int n1 = rand.nextInt(den) + 1;
            int n2 = rand.nextInt(den) + 1;
            return new int[]{n1, den, n2, den};
        }

        int[] f1 = createSingleFraction(maxDen, forcedNum);
        int[] f2 = createSingleFraction(maxDen, forcedNum);
        while (level <= 5 && f2[1] == f1[1]) f2 = createSingleFraction(maxDen, forcedNum);
        return new int[]{f1[0], f1[1], f2[0], f2[1]};
    }

    private int[] createSingleFraction(int maxDen, int forcedNum) {
        int den = rand.nextInt(maxDen - 1) + 2;
        int num = (forcedNum > 0) ? forcedNum : (forcedNum == 0 ? rand.nextInt(5) + 1 : rand.nextInt(den) + 1);
        return new int[]{num, den};
    }

    private String fractionDisplay(int numerator, int denominator) {
        if (denominator == 0) return "∞";
        if (numerator % denominator == 0) return String.valueOf(numerator / denominator);
        return numerator + "/" + denominator;
    }
}