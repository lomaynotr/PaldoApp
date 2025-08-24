PaldoApp: Mushroom Harvest Tracker 🍄
https://img.shields.io/badge/Kotlin-1.8.0-blue.svg
https://img.shields.io/badge/Android-12%252B-green.svg
https://img.shields.io/badge/Firebase-Auth%2520%2526%2520Firestore-orange.svg

A production-ready Android application designed specifically for mushroom farming operations to accurately track daily harvests and ensure fair compensation for farmworkers.

🌟 Overview
PaldoApp solves a critical problem in agricultural operations where workers are paid per kilo harvested. It replaces error-prone paper logs with a digital solution that provides:

Real-time harvest tracking by category (C1, C2, C3, C4)

Automatic calculation of worker compensation

Cloud-synced data storage with offline capability

Comprehensive reporting and Excel export functionality

Transparent data access for both workers and managers

📱 Features
Core Functionality
Secure Authentication - Email/password and anonymous login options

Daily Harvest Tracking - Intuitive input of mushroom weights by category

Real-time Calculations - Automatic compensation calculations based on preset prices

Data Management - View, edit, and delete historical entries

Monthly Filtering - Organize and view data by specific months

Advanced Features
Smart Reporting - Comprehensive summary views with totals and analytics

Excel Export - One-tap export to .XLS format for payroll processing

Sunday Highlighting - Automatic detection and special formatting for Sundays

Performance Tracking - Visual indicators for high-yield days (>500kg)

🛠️ Technical Stack
Language: Kotlin

Architecture: Model-View-Intent (MVI) Pattern

Database: Firebase Firestore (NoSQL, real-time sync)

Authentication: Firebase Auth

UI: Android Jetpack Components, Material Design

Export: JXL for Excel file generation

📸 Screenshots
Authentication	Data Entry	Data View	Reports
<img src="screenshots/auth.jpg" width="200">	<img src="screenshots/entry.jpg" width="200">	<img src="screenshots/view.jpg" width="200">	<img src="screenshots/reports.jpg" width="200">
🚀 Installation
Clone the repository

bash
git clone https://github.com/your-username/paldoapp.git
Open in Android Studio

Open Android Studio and select "Open an Existing Project"

Navigate to the cloned repository folder

Configure Firebase

Create a new Firebase project at Firebase Console

Enable Authentication and Firestore Database

Download the google-services.json file and place it in the app/ directory

Build and Run

Connect an Android device or emulator (API 21+)

Build the project and run on your device

📊 Usage
Authentication: Sign up or login with your credentials

Daily Entry:

Select the current date

Input harvest weights for each mushroom category

Save data to cloud storage

View History:

Browse past entries in the Data tab

Filter by month using the month selector

Edit or delete previous entries as needed

Generate Reports:

Navigate to the Print/Summary section

Review monthly totals and analytics

Export to Excel for payroll processing

🏗️ Project Structure
text
app/
├── src/main/
│   ├── java/com/example/paldoapp/
│   │   ├── AuthActivity.kt      # User authentication
│   │   ├── MainActivity.kt      # Daily data entry
│   │   ├── DataActivity.kt      # Historical data viewing
│   │   ├── PrintActivity.kt     # Reporting and export
│   │   └── SplashActivity.kt    # App launch screen
│   └── res/
│       ├── layout/              # XML layout files
│       ├── drawable/            # Images and icons
│       ├── menu/                # Navigation menus
│       └── values/              # Strings, colors, dimensions
🔧 Configuration
Firebase Setup
Create a new project in the Firebase Console

Enable Email/Password authentication in the Authentication section

Create a Firestore Database with the following rules:

javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /counter_data/{document} {
      allow read, write: if request.auth != null;
    }
  }
}
Download the google-services.json file and add it to your app

Category Prices
Update the category prices in MainActivity.kt:

kotlin
private val categoryPrices = mapOf(
    "C1" to 60,   // Price per kg for Category 1
    "C2" to 45,   // Price per kg for Category 2
    "C3" to 35,   // Price per kg for Category 3
    "C4" to 22    // Price per kg for Category 4
)
🤝 Contributing
We welcome contributions to PaldoApp! Please feel free to:

Fork the repository

Create a feature branch (git checkout -b feature/amazing-feature)

Commit your changes (git commit -m 'Add some amazing feature')

Push to the branch (git push origin feature/amazing-feature)

Open a Pull Request

📄 License
This project is licensed under the MIT License - see the LICENSE.md file for details.

📞 Support
If you have any questions or need help with implementation:

Create an Issue on GitHub

Connect with me on LinkedIn

Email me at your-email@example.com

🙏 Acknowledgments
Icons provided by Material Design Icons

Excel export functionality using Java Excel API

UI inspiration from Material Design Guidelines

<div align="center">
If this project helped you or your organization, please give it a ⭐️ on GitHub!

</div>
