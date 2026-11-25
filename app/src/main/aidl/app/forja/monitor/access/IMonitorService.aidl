package app.forja.monitor.access;

interface IMonitorService {
    String readThermalZones();
    String listSurfaceLayers();
    String readSurfaceLatency(String layerName);
}
