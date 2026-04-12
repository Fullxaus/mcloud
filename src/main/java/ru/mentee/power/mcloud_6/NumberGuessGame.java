package ru.mentee.power.mcloud_6;

import java.util.Random;
import java.util.Scanner;

public class NumberGuessGame {
  public static void main(String[] args) {
    Random random = new Random();
    Scanner scanner = new Scanner(System.in);

    int secretNumber = random.nextInt(100) + 1;
    int attempts = 0;
    boolean guessed = false;

    System.out.println("Добро пожаловать в игру \"Угадай число\"!");
    System.out.println("Я загадал число от 1 до 100. Попробуйте угадать.");

    while (!guessed) {
      System.out.print("Ваше предположение: ");
      int guess = scanner.nextInt();
      attempts++;

      if (guess < secretNumber) {
        System.out.println("Больше");
      } else if (guess > secretNumber) {
        System.out.println("Меньше");
      } else {
        guessed = true;
        System.out.println("Поздравляю! Вы угадали число " + secretNumber);
        System.out.println("Количество попыток: " + attempts);
      }
    }

    scanner.close();
  }
}
