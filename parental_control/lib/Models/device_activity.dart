class DeviceActivity {
  final String type;
  final String packageName;
  final String appName;
  final String details;
  final DateTime timestamp;
  final String formattedTime;

  DeviceActivity({
    required this.type,
    required this.packageName,
    required this.appName,
    required this.details,
    required this.timestamp,
    required this.formattedTime,
  });

  factory DeviceActivity.fromJson(Map<String, dynamic> json) {
    return DeviceActivity(
      type: json['type'],
      packageName: json['packageName'],
      appName: json['appName'],
      details: json['details'],
      timestamp: DateTime.fromMillisecondsSinceEpoch(json['timestamp']),
      formattedTime: json['formattedTime'],
    );
  }
}
