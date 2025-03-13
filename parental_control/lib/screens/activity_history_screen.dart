import 'package:flutter/material.dart';
import '../Models/device_activity.dart';
import '../services/activity_tracking_service.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'dart:convert';

class ActivityHistoryScreen extends StatefulWidget {
  const ActivityHistoryScreen({super.key});

  @override
  State<ActivityHistoryScreen> createState() => _ActivityHistoryScreenState();
}

class _ActivityHistoryScreenState extends State<ActivityHistoryScreen> {
  final ActivityTrackingService _trackingService = ActivityTrackingService();
  final List<DeviceActivity> _activities = [];
  bool _isServiceEnabled = false;

  @override
  void initState() {
    super.initState();
    _checkService();
    _setupActivityListener();
  }

  Future<void> _checkService() async {
    final isEnabled = await _trackingService.isTrackingEnabled();
    if (mounted) {
      setState(() {
        _isServiceEnabled = isEnabled;
      });
    }
    print("Service enabled: $isEnabled"); // Debug log
  }

  void _setupActivityListener() {
    const channel = EventChannel('com.example.parental_control/activity_events');
    channel.receiveBroadcastStream().listen((dynamic event) {
      try {
        final activityData = DeviceActivity.fromJson(
          json.decode(event.toString()) as Map<String, dynamic>
        );
        setState(() {
          _activities.insert(0, activityData); // Thêm vào đầu danh sách
        });
      } catch (e) {
        print('Error processing activity data: $e');
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Lịch sử hoạt động'),
        actions: [
          // Thêm nút refresh để kiểm tra lại trạng thái
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _checkService,
          ),
        ],
      ),
      body: FutureBuilder<bool>(
        // Sử dụng FutureBuilder để đảm bảo có kết quả mới nhất
        future: _trackingService.isTrackingEnabled(),
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }

          final isEnabled = snapshot.data ?? false;

          if (!isEnabled) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Text(
                    'Cần bật dịch vụ theo dõi hoạt động',
                    style: TextStyle(fontSize: 16),
                  ),
                  const SizedBox(height: 20),
                  ElevatedButton(
                    onPressed: () async {
                      await _trackingService.openTrackingSettings();
                      await _checkService(); // Kiểm tra lại sau khi mở settings
                    },
                    child: const Text('Bật dịch vụ'),
                  ),
                ],
              ),
            );
          }

          return _activities.isEmpty
              ? const Center(
                  child: Text('Chưa có hoạt động nào được ghi nhận'),
                )
              : ListView.builder(
                  itemCount: _activities.length,
                  itemBuilder: (context, index) {
                    final activity = _activities[index];
                    return Card(
                      margin: const EdgeInsets.symmetric(
                        horizontal: 8.0,
                        vertical: 4.0,
                      ),
                      child: ListTile(
                        leading: _getActivityIcon(activity.type),
                        title: Text(activity.appName),
                        subtitle: Text(activity.details),
                        trailing: Text(activity.formattedTime),
                      ),
                    );
                  },
                );
        },
      ),
    );
  }

  Widget _getActivityIcon(String type) {
    switch (type) {
      case 'APP_LAUNCH':
        return const Icon(Icons.launch, color: Colors.green);
      case 'APP_EXIT':
        return const Icon(Icons.close, color: Colors.red);
      case 'ACTIVITY':
        return const Icon(Icons.window, color: Colors.blue);
      default:
        return const Icon(Icons.info);
    }
  }
}
