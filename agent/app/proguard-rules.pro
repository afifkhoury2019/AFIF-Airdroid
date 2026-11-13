# Keep Paho's optional javax classes out of the shrinker's way.
-dontwarn org.eclipse.paho.**
-keep class org.eclipse.paho.client.mqttv3.** { *; }
-keep class org.eclipse.paho.android.service.** { *; }
