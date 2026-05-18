package hub

import (
	"context"
	"log/slog"
	"time"

	"github.com/coder/websocket"
)

const (
	sendBuffer   = 32               // queued messages before a client is considered slow
	writeTimeout = 10 * time.Second // per-message write deadline
	pingInterval = 25 * time.Second // keepalive ping cadence
)

// Client is one connected dashboard WebSocket. Messages are queued on send and
// drained to the socket by Run.
type Client struct {
	conn *websocket.Conn
	send chan []byte
}

// NewClient wraps an accepted WebSocket connection.
func NewClient(conn *websocket.Conn) *Client {
	return &Client{
		conn: conn,
		send: make(chan []byte, sendBuffer),
	}
}

// trySend queues a message without blocking. It returns false when the
// client's buffer is full (the hub then drops the client).
func (c *Client) trySend(msg []byte) bool {
	select {
	case c.send <- msg:
		return true
	default:
		return false
	}
}

// SendNow writes a message straight to the socket, bypassing the queue.
// The hub uses this for the one-off snapshot sent right after connect.
func (c *Client) SendNow(ctx context.Context, msg []byte) error {
	wctx, cancel := context.WithTimeout(ctx, writeTimeout)
	defer cancel()
	return c.conn.Write(wctx, websocket.MessageText, msg)
}

// Run drains queued messages to the socket and sends keepalive pings. It
// blocks for the lifetime of the connection and returns once the context is
// cancelled, the peer disconnects, or a write fails.
func (c *Client) Run(ctx context.Context) {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	// Dashboards are not expected to send data, but a reader is still needed
	// to process control frames (close/pong) and to notice a disconnect.
	go func() {
		defer cancel()
		for {
			if _, _, err := c.conn.Read(ctx); err != nil {
				return
			}
		}
	}()

	ticker := time.NewTicker(pingInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return

		case msg, ok := <-c.send:
			if !ok {
				return // hub closed the channel (slow-client drop or shutdown)
			}
			if err := c.SendNow(ctx, msg); err != nil {
				slog.Debug("dashboard write failed", "err", err)
				return
			}

		case <-ticker.C:
			pctx, pcancel := context.WithTimeout(ctx, writeTimeout)
			err := c.conn.Ping(pctx)
			pcancel()
			if err != nil {
				return
			}
		}
	}
}
