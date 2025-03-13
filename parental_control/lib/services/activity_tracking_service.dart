import 'package:flutter/services.dart';
import '../Models/device_activity.dart';

class ActivityTrackingService {
  static const platform = MethodChannel('com.example.parental_control/activity_tracking');
  
  Future<bool> isTrackingEnabled() async {
    try {
      final bool result = await platform.invokeMethod('isTrackingServiceEnabled');
      print('Tracking service status: $result'); // Debug log
      return result;
    } catch (e) {
      print('Error checking tracking service: $e');
      return false;
    }
  }

  Future<void> openTrackingSettings() async {
    try {
      await platform.invokeMethod('openTrackingSettings');
    } catch (e) {
      print('Error opening tracking settings: $e');
    }
  }
}
