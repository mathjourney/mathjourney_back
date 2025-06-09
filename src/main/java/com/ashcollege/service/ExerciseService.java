package com.ashcollege.service;

import com.ashcollege.entities.UserEntity;
import com.ashcollege.entities.UserTopicLevelEntity;
import com.ashcollege.repository.UserRepository;
import com.ashcollege.repository.UserTopicLevelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*; // ✅ תוספת ל-Set, HashSet, Map וכו'

@Service
public class ExerciseService {
    private static final Logger logger = LoggerFactory.getLogger(ExerciseService.class);

    @Autowired
    private UserService userService;

    @Autowired
    private UserTopicLevelRepository userTopicLevelRepo;

    @Autowired
    private UserRepository userRepo;

    private final Random rand = new Random();

    public Map<String, Object> generateQuestion(int topicId) {
        UserEntity user = userService.getCurrentUser();
        if (user == null) {
            throw new RuntimeException("No current user found");
        }

        UserTopicLevelEntity ute = userTopicLevelRepo.findByUserIdAndTopicId(user.getId(), topicId);
        if (ute == null) {
            ute = new UserTopicLevelEntity();
            ute.setUserId(user.getId());
            ute.setTopicId(topicId);
            ute.setLevel(0); // במקום 1
            ute.setMistakes(0);
            ute.setAttempts(0);
            userTopicLevelRepo.save(ute);
        }
        int currentLevel = ute.getLevel();
        logger.info("Topic {} => CurrentLevel={}", topicId, currentLevel);

        Map<String, Object> question;
        switch (topicId) {
            case 1: question = generateBasicArithmetic("+", currentLevel); break;
            case 2: question = generateBasicArithmetic("-", currentLevel); break;
            case 3: question = generateBasicArithmetic("×", currentLevel); break;
            case 4: question = generateBasicArithmetic("÷", currentLevel); break;
            case 5: question = generateFractionQuestion("+", currentLevel); break;
            case 6: question = generateFractionQuestion("-", currentLevel); break;
            case 7: question = generateFractionQuestion("×", currentLevel); break;
            case 8: question = generateFractionQuestion("÷", currentLevel); break;
            default: question = generateBasicArithmetic("+", currentLevel);
        }

        question.put("topicId", topicId);
        return question;
    }

    public void increaseUserTopicLevel(int userId, int topicId) {
        UserTopicLevelEntity rec = userTopicLevelRepo.findByUserIdAndTopicId(userId, topicId);
        if (rec != null) {
            int oldLevel = rec.getLevel();
            rec.setLevel(oldLevel + 1);
            userTopicLevelRepo.save(rec);
            logger.info("User {} in topic {} => level up from {} to {}", userId, topicId, oldLevel, rec.getLevel());

            System.out.println("📥 Updating topic level...");
            // ✅ עדכון רמה כללית לפי ממוצע
            updateGeneralLevel(userId);
        }
    }
    /**
     * ממיר מונה ומכנה לטקסט תצוגה:
     * - אם num%den==0 => מציג כמספר שלם
     * - אחרת => מציג "num/den"
     */

    private String fractionDisplay(int numerator, int denominator) {
        if (denominator == 0) {
            // הגנה מפני חלוקה ב-0, אם בטעות נוצר
            return "∞";
        }
        // אם המונה מתחלק בדיוק במכנה => להציג כמספר שלם
        if (numerator % denominator == 0) {
            return String.valueOf(numerator / denominator);
        } else {
            // להציג כמות שהוא, בלי צמצום מלא
            return numerator + "/" + denominator;
        }
    }

    public int getUserTopicLevel(int userId, int topicId) {
        UserTopicLevelEntity rec = userTopicLevelRepo.findByUserIdAndTopicId(userId, topicId);
        if (rec == null) return 1;
        return rec.getLevel();
    }

    public void updateGeneralLevel(int userId) {
        List<UserTopicLevelEntity> levels = userTopicLevelRepo.findByUserId(userId);
        if (levels.isEmpty()) return;

        // מסננים רק נושאים שהתחילו בהם
        List<UserTopicLevelEntity> progressed = levels.stream()
                .filter(l -> l.getLevel() > 0)
                .toList();

        if (progressed.isEmpty()) {
            UserEntity user = userRepo.findById(userId).orElse(null);
            if (user != null) {
                user.setLevel(0);
                userRepo.save(user);
            }
            return;
        }

        int minLevel = progressed.stream().mapToInt(UserTopicLevelEntity::getLevel).min().orElse(0);

        UserEntity user = userRepo.findById(userId).orElse(null);
        if (user != null) {
            user.setLevel(minLevel);
            userRepo.save(user);
        }

        System.out.println("🟣 GENERAL LEVEL CALCULATED (MIN): " + minLevel);
    }

    public boolean checkAnswer(Map<String, Object> question, int userAnswer) {
        int correct = (int) question.get("correctAnswer");
        return (userAnswer == correct);
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

    private int[] generateUniqueAnswers(int correctAnswer) {
        Set<Integer> uniqueAnswers = new HashSet<>();
        uniqueAnswers.add(correctAnswer); // תמיד מוסיפים את התשובה הנכונה

        int attempts = 0;

        // כל עוד צריך עוד תשובות, וניסינו פחות מ-100 פעמים
        while (uniqueAnswers.size() < 4 && attempts < 100) {
            int offset = rand.nextInt(11) - 5; // טווח של -5 עד +5
            int candidate = correctAnswer + offset;

            // רק נוודא שהוא לא שלילי – מותר להיות 0!
            if (candidate < 0) continue;

            uniqueAnswers.add(candidate);
            attempts++;
        }

        // אם לא הצלחנו לייצר 4 תשובות שונות (מאוד נדיר) – נוסיף תשובות אקראיות
        while (uniqueAnswers.size() < 4) {
            uniqueAnswers.add(rand.nextInt(20)); // טווח רזרבי
        }

        int[] arr = uniqueAnswers.stream()
                .mapToInt(Integer::intValue)
                .toArray();

        shuffleArray(arr); // ערבוב

        return arr;
    }

    private Map<String, Object> generateBasicArithmetic(String sign, int level) {
        int a = 0, b = 0, correct = 0;
        boolean valid = false;
        int attempts = 0;

        if (sign.equals("÷")) {
            int maxA = level <= 5 ? level * 10 : 50 + (level - 5) * 50;
            int maxResult = level <= 5 ? 4 + level : 10 + (level - 5) * 2;
            int minA = Math.max(4, maxA / 5);

            while (!valid && attempts < 100) {
                attempts++;
                a = rand.nextInt(maxA - minA + 1) + minA;

                List<Integer> goodDivisors = new ArrayList<>();
                for (int i = 2; i <= a / 2; i++) {
                    if (a % i == 0) {
                        int result = a / i;
                        if (result > 1 && result <= maxResult) {
                            goodDivisors.add(i);
                        }
                    }
                }

                if (!goodDivisors.isEmpty()) {
                    b = goodDivisors.get(rand.nextInt(goodDivisors.size()));
                    correct = a / b;
                    valid = true;
                }
            }
        } else {
            int minVal = (level - 1) * 5;
            int maxVal = level * 5;

            while (!valid && attempts < 100) {
                attempts++;

                if (sign.equals("-")) {
                    int minDifference = switch (level) {
                        case 1, 2 -> 3;
                        case 3, 4 -> 6;
                        case 5, 6 -> 10;
                        default -> 20;
                    };

                    int maxValSub = level * 10;
                    int minValSub = Math.max(2, level * 2);

                    b = rand.nextInt(maxValSub / 2 - minValSub + 1) + minValSub;
                    a = b + minDifference + rand.nextInt(maxValSub / 2);
                    correct = a - b;

                    if (correct >= 0) {
                        valid = true;
                    }

                } else {
                    a = rand.nextInt(maxVal - minVal + 1) + minVal;
                    b = rand.nextInt(maxVal - minVal + 1) + minVal;

                    switch (sign) {
                        case "+" -> correct = a + b;
                        case "×" -> correct = a * b;
                    }
                    valid = true;
                }
            }
        }

        if (!valid) {
            throw new RuntimeException("ניסיון ליצור שאלה נכשל לאחר 100 ניסיונות (פעולה: " + sign + ", רמה: " + level + ")");
        }

        int[] answers = generateUniqueAnswers(correct);
        boolean allNegative = Arrays.stream(answers).allMatch(x -> x < 0);
        if (allNegative) {
            return generateBasicArithmetic(sign, level); // נסיון נוסף עם רקורסיה רק אם הכל שלילי
        }

        Map<String, Object> q = new HashMap<>();
        q.put("first", Math.max(a, b));
        q.put("second", Math.min(a, b));
        q.put("operationSign", sign);
        q.put("correctAnswer", correct);
        q.put("answers", answers);

        return q;
    }



    private Map<String, Object> generateFractionQuestion(String sign, int level) {
        int[] frac = createFractionPair(level);
        int a = frac[0];
        int b = frac[1];
        int c = frac[2];
        int d = frac[3];

        // בדיקה: לחיסור, נוודא שהמונה גדול מספיק כדי לא לקבל תוצאה שלילית:
        if (sign.equals("-") && (a * d < c * b)) {
            return generateFractionQuestion(sign, level);
        }

        // חישוב תוצאת השבר הנכון
        int num = 0, den = 0;
        switch (sign) {
            case "+":
                if (b == d) {
                    num = a + c;
                    den = b;
                } else {
                    num = a * d + b * c;
                    den = b * d;
                }
                break;
            case "-":
                if (b == d) {
                    num = a - c;
                    den = b;
                } else {
                    num = a * d - b * c;
                    den = b * d;
                }
                break;
            case "×":
            case "*": // לכל מקרה
                num = a * c;
                den = b * d;
                break;
            case "÷":
            case "/":
                num = a * d;
                den = b * c;
                break;
        }

        // אם נוצר שבר שלילי או מכנה אפס => קריאה חוזרת
        if (num < 0 || den <= 0) {
            return generateFractionQuestion(sign, level);
        }

        // כאן נשמור את הרשימה הסופית של התשובות (encoded),
        // **אבל** נעקוב במקביל אחרי הטקסט שמוצג למשתמש (כדי למנוע כפילויות חזותיות)
        List<Integer> answersList = new ArrayList<>();
        Set<String> displayStrings = new HashSet<>();

        // התשובה הנכונה ב-encoded
        int correctEncoded = num * 1000 + den;
        // התצוגה למשתמש: אם זה מתחלק בשלמות => רק מספר שלם, אחרת "num/den"
        String correctDisplay = fractionDisplay(num, den);

        // מוסיפים את התשובה הנכונה
        answersList.add(correctEncoded);
        displayStrings.add(correctDisplay);

        // הגרלת עוד תשובות
        int attempts = 0;
        while (answersList.size() < 4 && attempts < 100) {
            // אפשר להגדיל טווח אם רוצים יותר שונות
            int offsetNum = rand.nextInt(5) - 2;
            int offsetDen = rand.nextInt(3);
            int newNum = Math.max(0, num + offsetNum);  // 0 חוקי לגמרי כתוצאה
            int newDen = Math.max(1, den + offsetDen);

            String candidateDisplay = fractionDisplay(newNum, newDen);

            // רק אם זה תצוגה חדשה ולא קיימת כבר, נוסיף
            if (!displayStrings.contains(candidateDisplay)) {
                answersList.add(newNum * 1000 + newDen);
                displayStrings.add(candidateDisplay);
            }

            attempts++;
        }

        if (correctEncoded ==0){
            answersList.add(0);
            displayStrings.add("0");

        }

        // המרת הרשימה למערך, ערבוב סדר (shuffle)
        int[] answers = answersList.stream().mapToInt(Integer::intValue).toArray();
        shuffleArray(answers);

        // בניית אובייקט ה-JSON להחזרה
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

        boolean sameDen = false;
        boolean differDen = false;
        int maxDen = 5;
        int forcedNum = -1;

        if (level == 1) {
            sameDen = true; maxDen = 5;
        } else if (level == 2) {
            sameDen = true; maxDen = 10;
        } else if (level == 3) {
            sameDen = true; maxDen = 20;
        } else if (level == 4) {
            differDen = true; maxDen = 5; forcedNum = 1;
        } else if (level == 5) {
            differDen = true; maxDen = 10; forcedNum = 1;
        } else {
            differDen = true;
            int offset = 5 * (level - 5);
            maxDen = Math.max(offset, 5);
            forcedNum = 0;
        }

        if (sameDen) {
            int den = rand.nextInt(maxDen - 1) + 2;
            int n1 = rand.nextInt(den) + 1;
            int n2 = rand.nextInt(den) + 1;
            return new int[]{n1, den, n2, den};
        }

        int[] f1 = createSingleFraction(maxDen, forcedNum);
        int[] f2 = createSingleFraction(maxDen, forcedNum);

        if (level <= 5) {
            while (f2[1] == f1[1]) {
                f2 = createSingleFraction(maxDen, forcedNum);
            }
        }

        return new int[]{f1[0], f1[1], f2[0], f2[1]};
    }

    private int[] createSingleFraction(int maxDen, int forcedNum) {
        int den = rand.nextInt(maxDen - 1) + 2;
        int num = (forcedNum > 0) ? forcedNum :
                (forcedNum == 0 ? rand.nextInt(5) + 1 : rand.nextInt(den) + 1);
        return new int[]{num, den};
    }

    private void shuffleArray(int[] arr) {
        for (int i = arr.length - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            int tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }
    }
}