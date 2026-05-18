package com.example.livegps.data.model

/** Result of the most recent attempt to upload buffered fixes to the backend. */
enum class ConnectionState {
    IDLE,       // nothing sent yet this session
    CONNECTING, // an upload is in progress
    ONLINE,     // last upload succeeded
    OFFLINE,    // last upload failed — fixes are buffering locally
}
