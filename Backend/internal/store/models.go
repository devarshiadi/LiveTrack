package store

// Device is one row of the devices table — a single tracked phone.
type Device struct {
	ID                 string
	Name               string
	Battery            *int // last reported battery %, nil if unknown
	CaptureIntervalSec *int // per-device interval override, nil = use global default
	FirstSeenAt        int64
	LastSeenAt         int64
}

// Location is one row of the locations table — a single GPS fix.
type Location struct {
	ID         int64
	DeviceID   string
	Lat        float64
	Lng        float64
	Accuracy   *float64
	Speed      *float64
	Bearing    *float64
	Battery    *int
	Timestamp  int64 // device clock, unix milliseconds
	ReceivedAt int64 // server clock, unix milliseconds
}

// DeviceWithLocation pairs a device with its most recent fix.
type DeviceWithLocation struct {
	Device
	Last *Location
}

// Geofence is a circular zone on the map.
type Geofence struct {
	ID        int64
	Name      string
	Lat       float64
	Lng       float64
	RadiusM   float64
	CreatedAt int64
}

// Alert is a notable event surfaced on the dashboard.
type Alert struct {
	ID           int64
	DeviceID     string
	Type         string
	Severity     string
	Message      string
	CreatedAt    int64
	Acknowledged bool
}
