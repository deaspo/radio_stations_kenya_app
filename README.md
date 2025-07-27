# **Kenya Radio \- Android App**

An open-source native Android application for streaming Kenyan radio stations. This app scrapes station data from the [radio.or.ke](https://radio.or.ke/) website and provides a clean, native interface for listening, background playback, and casting.

## **Features**

* **Native Streaming:** Listen to dozens of Kenyan radio stations directly in the app without a web browser.
* **Google Cast:** Cast audio to any Google Cast-enabled device on your network (e.g., Chromecast, Google Home/Nest speakers).
* **Background Playback:** Keep listening to the radio while using other apps or when your screen is off, with a full-featured media notification.
* **Clean UI:** A simple, modern grid layout built with Google's Material Design guidelines.
* **Rotation Resilient:** Playback continues uninterrupted when you rotate your screen.
* **Seamless Transfers:** Automatically transfer playback between your phone and a cast device when you connect or disconnect.

## **How It Works**

The app works in two main stages:

1. **Scraping Station List:** On startup, the app uses the [Jsoup](https://jsoup.org/) library to scrape the main page of radio.or.ke. It parses the HTML to get the list of stations, including their name, logo, and a unique ID (e.g., "classic-105").
2. **Fetching Stream URL:** When you select a station, the app makes a request to a hidden API endpoint used by the website (https://api.instant.audio/...). This API returns a JSON object containing the actual, playable stream URL (HLS, AAC, or MP3) and other details like the station's signal frequency.
3. **Playback:** This final stream URL is then passed to Android's **Media3 ExoPlayer** for robust local playback or to the **Google Cast framework** for remote playback.

## **Screenshots**

*(Replace these placeholders with actual screenshots of your app)*

| Main Screen | Player Controls | Notification |
| :---- | :---- | :---- |
| <img width="240" height="2529" alt="image" src="https://github.com/user-attachments/assets/17a8d29b-c9db-43cf-a4c0-c422c9b2f14e" />| <img width="240" height="2529" alt="image" src="https://github.com/user-attachments/assets/7160782d-0472-4e66-a676-545501ffdfde" />| <img width="240" height="2529" alt="image" src="https://github.com/user-attachments/assets/0ef54a57-8b22-4ecc-a76a-e5f6ed6ebfba" /> |

## **Building from Source**

To build and run this project yourself, follow these steps:

1. **Clone the repository:**  
   git clone \[Your-Repository-URL\]

2. **Open in Android Studio:** Open the cloned project folder in the latest version of Android Studio.
3. **Sync Gradle:** Let Android Studio download and sync all the required dependencies as defined in the build.gradle.kts files.
4. **Run the app:** Connect an Android device or start an emulator and click the "Run" button.

## **License**

This project is licensed under the **Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License**.

This means you are free to:

* **Share** — copy and redistribute the material in any medium or format.
* **Adapt** — remix, transform, and build upon the material.

Under the following terms:

* **Attribution (BY):** You must give appropriate credit to the original creator (that's you\!).
* **NonCommercial (NC):** You may not use the material for commercial purposes.
* **ShareAlike (SA):** If you remix, transform, or build upon the material, you must distribute your contributions under the same license as the original.

For the full license text, see [LICENSE.md](http://docs.google.com/LICENSE.md) or visit [creativecommons.org/licenses/by-nc-sa/4.0/](https://creativecommons.org/licenses/by-nc-sa/4.0/).
