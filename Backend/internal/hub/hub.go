// Package hub fans out live location updates to every connected web dashboard
// over WebSocket. A single goroutine (Run) owns all shared state, so the
// client set needs no mutex — the classic Go "share by communicating" pattern.
package hub

import "log/slog"

// Hub manages the set of connected dashboard clients and broadcasts to them.
type Hub struct {
	register   chan *Client
	unregister chan *Client
	broadcast  chan []byte
	clients    map[*Client]struct{} // owned solely by Run
	done       chan struct{}
}

// New creates an idle Hub. Call Run in its own goroutine to start it.
func New() *Hub {
	return &Hub{
		register:   make(chan *Client),
		unregister: make(chan *Client),
		broadcast:  make(chan []byte, 256),
		clients:    make(map[*Client]struct{}),
		done:       make(chan struct{}),
	}
}

// Run is the hub's event loop. It blocks until Close is called.
func (h *Hub) Run() {
	for {
		select {
		case <-h.done:
			for c := range h.clients {
				close(c.send)
				delete(h.clients, c)
			}
			return

		case c := <-h.register:
			h.clients[c] = struct{}{}
			slog.Info("dashboard connected", "clients", len(h.clients))

		case c := <-h.unregister:
			if _, ok := h.clients[c]; ok {
				delete(h.clients, c)
				close(c.send)
				slog.Info("dashboard disconnected", "clients", len(h.clients))
			}

		case msg := <-h.broadcast:
			for c := range h.clients {
				if !c.trySend(msg) {
					// Slow client: its buffer is full. Drop it rather than
					// block every other dashboard — the browser reconnects
					// and re-syncs via a fresh snapshot.
					delete(h.clients, c)
					close(c.send)
					slog.Warn("dropped slow dashboard client")
				}
			}
		}
	}
}

// Register adds a client. Safe to call after Close (it becomes a no-op).
func (h *Hub) Register(c *Client) {
	select {
	case h.register <- c:
	case <-h.done:
	}
}

// Unregister removes a client. Safe to call after Close, and idempotent
// (the Run loop only closes a client's send channel once).
func (h *Hub) Unregister(c *Client) {
	select {
	case h.unregister <- c:
	case <-h.done:
	}
}

// Broadcast queues a pre-marshalled message for every dashboard. It never
// blocks: if the hub's own buffer is full the message is dropped.
func (h *Hub) Broadcast(msg []byte) {
	select {
	case h.broadcast <- msg:
	case <-h.done:
	default:
		slog.Warn("hub broadcast buffer full; dropping update")
	}
}

// Close stops the Run loop and disconnects all clients.
func (h *Hub) Close() { close(h.done) }
