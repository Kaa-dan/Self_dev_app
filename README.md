# WorkTrack Lite

A small personal Android app (Kotlin + Jetpack Compose + Room) for two things: **money** and **daily habits**, plus **goals** with an end date. Everything is stored on the phone; there's no account or cloud.

## Screens

- **Dashboard**: habit streak badge, money tiles (spent today and this week, this month against the limit, bank balance, savings, total debt, money to receive, salary), unpaid monthly payments and the nearest goals. Tap a tile to open its section.
- **Money**
  - **Spend**: salary (with "mark received", which adds it to a bank), monthly limit, and spends by month and category. A spend paid from a bank comes off that bank's balance.
  - **Monthly**: EMIs, rent, credit card bills and other monthly payments, each marked paid for the month. Paying an EMI that's linked to a loan lowers what's left on the loan.
  - **Banks**: accounts and balances.
  - **Loans**: amount, what's left to repay, EMI, and how much is repaid.
  - **To give / get**: money between you and other people, with part payments.
  - **Savings**: FDs, emergency fund, investments…
- **Habits**: daily habits with an optional time (reminder notification with a *Done* button) and duration, a streak for each habit, the last 7 days at a glance, and ticking off past days.
- **Goals**: a goal and its end date, with days left. Overdue goals show in red.

**Total debt** = what's left on all loans + what you still have to give people.
**Streak** = days in a row with every habit done. Badges unlock at 3, 7, 14, 30, 60, 100 and 365 days.

## Build

Needs JDK 17 and the Android SDK (compileSdk 37).

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest
```

The application id is `com.nithinraj.worktrack.simple`, so it installs next to the full WorkTrack app instead of replacing it.
Release signing is read from `keystore.properties`, the same way as on `main`.
