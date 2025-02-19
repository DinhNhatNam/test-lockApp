import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../services/app_usage_service.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final AppUsageService _appUsageService = AppUsageService();
  List<Map<String, String>> _apps = [];
  static const platform = MethodChannel('com.example.parental_control/app_usage');
  bool _hasAdminPermission = false;
  bool _hasUsagePermission = false;

  @override
  void initState() {
    super.initState();
    _checkPermissionsAndLoadApps();
  }

 Future<void> _checkPermissionsAndLoadApps() async {
    try {
      final Map<dynamic, dynamic> permissions = await platform.invokeMethod('checkPermissions');
      debugPrint('Permissions: $permissions');
      
      setState(() {
        _hasAdminPermission = permissions['admin'] == true;
        _hasUsagePermission = permissions['usageStats'] == true;
      });

      if (!_hasUsagePermission) {
        debugPrint('Requesting usage stats permission');
        await platform.invokeMethod('requestUsagePermission');
        return;
      }

      await _loadApps();
    } catch (e) {
      debugPrint('Error checking permissions: $e');
    }
  }

  Future<void> _requestPermissions() async {
    if (!_hasUsagePermission) {
      await platform.invokeMethod('requestUsagePermission');
    }
    if (!_hasAdminPermission) {
      await platform.invokeMethod('requestAdminPermission');
    }
    await _checkPermissionsAndLoadApps();
  }
  Future<void> _loadApps() async {
    try {
      debugPrint('Loading apps...');
      final apps = await _appUsageService.getInstalledApps();
      setState(() {
        _apps = apps;
      });
      debugPrint('Loaded ${_apps.length} apps');
    } catch (e) {
      debugPrint('Error loading apps: $e');
    }
  }

Future<void> _showPermissionDialog(BuildContext context) async {
    return showDialog(
      context: context,
      barrierDismissible: false, // Người dùng phải chọn một option
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('Yêu cầu quyền'),
          content: const Text(
            'Ứng dụng cần quyền quản trị thiết bị để có thể chặn các ứng dụng khác. '
            'Vui lòng cấp quyền để tiếp tục.',
          ),
          actions: <Widget>[
            TextButton(
              child: const Text('Để sau'),
              onPressed: () {
                Navigator.of(context).pop();
              },
            ),
            TextButton(
              child: const Text('Cấp quyền'),
              onPressed: () async {
                Navigator.of(context).pop();
                // Đợi một chút để dialog đóng hoàn toàn
                await Future.delayed(const Duration(milliseconds: 500));
                // Gọi method để yêu cầu quyền
                await platform.invokeMethod('requestAdminPermission');
                // Kiểm tra lại quyền sau khi yêu cầu
                await _checkPermissionsAndLoadApps();
              },
            ),
          ],
        );
      },
    );
  }


  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Quản lý ứng dụng'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _checkPermissionsAndLoadApps,
          ),
        ],
      ),
body: !_hasUsagePermission
    ? Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Text(
              'Cần quyền truy cập dữ liệu sử dụng\nđể xem danh sách ứng dụng',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 16),
            ),
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: () async {
                await platform.invokeMethod('requestUsagePermission');
                await _checkPermissionsAndLoadApps();
              },
              child: const Text('Cấp quyền'),
            ),
          ],
        ),
      )
    : _apps.isEmpty
        ? const Center(child: CircularProgressIndicator())
        : ListView.builder(
            itemCount: _apps.length,
            itemBuilder: (context, index) {
              final app = _apps[index];
              return ListTile(
                leading: const Icon(Icons.apps),
                title: Text(app['appName'] ?? 'Unknown'),
                subtitle: Text(app['packageName'] ?? ''),
                trailing: FutureBuilder<bool>(
                  future: _appUsageService.isAppBlocked(app['packageName'] ?? ''),
                  builder: (context, snapshot) {
                    final isBlocked = snapshot.data ?? false;
                    return Switch(
                      value: isBlocked,
                      onChanged: (bool value) async {
                        if (!_hasAdminPermission) {
                          await _showPermissionDialog(context);
                          return;
                        }
                        
                        final success = await _appUsageService.blockApp(
                          app['packageName'] ?? '',
                          value,
                        );
                        if (success) {
                          setState(() {}); // Refresh UI
                        }
                      },
                    );
                  },
                ),
              );
            },
          ),
    );
  }
}