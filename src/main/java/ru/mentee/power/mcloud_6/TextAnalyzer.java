package ru.mentee.power.mcloud_6;

import java.util.HashMap;
import java.util.Map;

public class TextAnalyzer {
  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println("Использование: java TextAnalyzer \"текст для анализа\"");
      return;
    }

    String text = String.join(" ", args);
    analyzeText(text);
  }

  private static void analyzeText(String text) {
    System.out.println("Анализ текста");
    System.out.println("=============");

    int totalChars = text.length();
    int letters = 0;
    int digits = 0;
    int spaces = 0;

    for (char c : text.toCharArray()) {
      if (Character.isLetter(c)) {
        letters++;
      } else if (Character.isDigit(c)) {
        digits++;
      } else if (Character.isWhitespace(c)) {
        spaces++;
      }
    }

    String[] words = text.trim().isEmpty() ? new String[0] : text.split("\\s+");
    int wordCount = words.length;

    Map<String, Integer> wordFreq = new HashMap<>();
    for (String word : words) {
      word = word.toLowerCase();
      wordFreq.put(word, wordFreq.getOrDefault(word, 0) + 1);
    }

    System.out.println("Общее количество символов: " + totalChars);
    System.out.println("Буквы: " + letters);
    System.out.println("Цифры: " + digits);
    System.out.println("Пробелы: " + spaces);
    System.out.println("Количество слов: " + wordCount);
    System.out.println("ТОП-3 частых слова:");

    wordFreq.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(3)
        .forEach(e -> System.out.println("  " + e.getKey() + ": " + e.getValue()));
  }
}
