//go:build linux && !android

// Package winx cuida da janela no X11: tira a barra de título do sistema (o Sonivell GO desenha a
// sua, como no mockup) e pede ao gerenciador de janelas para mover/redimensionar.
//
// O Gio nunca desliga a decoração no X11, então isso é feito direto pelo Xlib (o Gio chama
// XInitThreads, então é seguro chamar daqui).
package winx

/*
#cgo pkg-config: x11
#include <X11/Xlib.h>
#include <string.h>

static void sv_undecorate(Display *d, Window w) {
	Atom hints = XInternAtom(d, "_MOTIF_WM_HINTS", False);
	// flags = MWM_HINTS_DECORATIONS (2), decorations = 0
	long data[5] = {2, 0, 0, 0, 0};
	XChangeProperty(d, w, hints, hints, 32, PropModeReplace, (unsigned char *)data, 5);
	XFlush(d);
}

static void sv_moveresize(Display *d, Window w, int dir) {
	Window root = DefaultRootWindow(d), rootRet, child;
	int rx = 0, ry = 0, wx, wy;
	unsigned int mask;
	XQueryPointer(d, w, &rootRet, &child, &rx, &ry, &wx, &wy, &mask);
	XUngrabPointer(d, CurrentTime);
	XEvent ev;
	memset(&ev, 0, sizeof ev);
	ev.xclient.type = ClientMessage;
	ev.xclient.window = w;
	ev.xclient.message_type = XInternAtom(d, "_NET_WM_MOVERESIZE", False);
	ev.xclient.format = 32;
	ev.xclient.data.l[0] = rx;
	ev.xclient.data.l[1] = ry;
	ev.xclient.data.l[2] = dir;
	ev.xclient.data.l[3] = Button1;
	ev.xclient.data.l[4] = 1;
	XSendEvent(d, root, False, SubstructureRedirectMask | SubstructureNotifyMask, &ev);
	XFlush(d);
}
*/
import "C"

import (
	"sync"
	"unsafe"
)

// Direções do _NET_WM_MOVERESIZE.
const (
	ResizeTopLeft     = 0
	ResizeTop         = 1
	ResizeTopRight    = 2
	ResizeRight       = 3
	ResizeBottomRight = 4
	ResizeBottom      = 5
	ResizeBottomLeft  = 6
	ResizeLeft        = 7
	Move              = 8
)

// Window guarda a janela X11 atual (zerada fora do X11, por exemplo no Wayland).
type Window struct {
	mu  sync.Mutex
	dpy unsafe.Pointer
	win uintptr
}

// Set registra a janela (vinda do app.X11ViewEvent) e tira a decoração do sistema.
func (w *Window) Set(display unsafe.Pointer, window uintptr) {
	w.mu.Lock()
	defer w.mu.Unlock()
	w.dpy, w.win = display, window
	if display != nil && window != 0 {
		C.sv_undecorate((*C.Display)(display), C.Window(window))
	}
}

// Valid diz se estamos numa janela X11.
func (w *Window) Valid() bool {
	w.mu.Lock()
	defer w.mu.Unlock()
	return w.dpy != nil && w.win != 0
}

// MoveResize começa a mover (dir = Move) ou redimensionar a janela com o mouse.
func (w *Window) MoveResize(dir int) {
	w.mu.Lock()
	defer w.mu.Unlock()
	if w.dpy == nil || w.win == 0 {
		return
	}
	C.sv_moveresize((*C.Display)(w.dpy), C.Window(w.win), C.int(dir))
}
