import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';

class AppUsageService {
  static const platform = MethodChannel('com.example.parental_control/app_usage');
  
  Future<List<Map<String, String>>> getInstalledApps() async {
    try {
      final List<dynamic> result = await platform.invokeMethod('getInstalledApps');
      return result.map((dynamic item) {
        return Map<String, String>.from(item as Map);
      }).toList();
    } catch (e) {
      debugPrint('Error getting installed apps: $e');
      return [];
    }
  }

  Future<bool> blockApp(String packageName, bool isBlocked) async {
    try {
      final bool result = await platform.invokeMethod('blockApp', {
        'packageName': packageName,
        'isBlocked': isBlocked,
      });
      return result;
    } catch (e) {
      debugPrint('Error blocking app: $e');
      return false;
    }
  }

  Future<bool> isAppBlocked(String packageName) async {
    try {
      final bool result = await platform.invokeMethod('isAppBlocked', {
        'packageName': packageName,
      });
      return result;
    } catch (e) {
      debugPrint('Error checking if app is blocked: $e');
      return false;
    }
  }

  Future<bool> isAccessibilityServiceEnabled() async {
    try {
      final bool result = await platform.invokeMethod('isAccessibilityServiceEnabled');
      return result;
    } catch (e) {
      debugPrint('Error checking accessibility service: $e');
      return false;
    }
  }

  Future<void> openAccessibilitySettings() async {
    try {
      await platform.invokeMethod('openAccessibilitySettings');
    } catch (e) {
      debugPrint('Error opening accessibility settings: $e');
    }
  }
}

class AppBlockService {
  static const platform = MethodChannel('com.example.parental_control/app_block');

  Future<bool> blockApp(String packageName, bool isBlocked) async {
    try {
      final bool result = await platform.invokeMethod('blockApp', {
        'packageName': packageName,
        'isBlocked': isBlocked,
      });
      return result;
    } on PlatformException catch (e) {
      debugPrint('Error blocking app: ${e.message}');
      return false;
    }
  }

  Future<bool> isAppBlocked(String packageName) async {
    try {
      final bool result = await platform.invokeMethod('isAppBlocked', {
        'packageName': packageName,
      });
      return result;
    } on PlatformException catch (e) {
      debugPrint('Error checking app block status: ${e.message}');
      return false;
    }
  }

  Future<bool> isAccessibilityServiceEnabled() async {
    try {
      final bool result = await platform.invokeMethod('isAccessibilityServiceEnabled');
      return result;
    } on PlatformException catch (e) {
      debugPrint('Error checking accessibility service: ${e.message}');
      return false;
    }
  }

  Future<void> openAccessibilitySettings() async {
    try {
      await platform.invokeMethod('openAccessibilitySettings');
    } on PlatformException catch (e) {
      debugPrint('Error opening settings: ${e.message}');
    }
  }
}