# UI specification

## Visual thesis

LightCopy feels like a pocket developer instrument: near-black graphite canvas, crisp blue-cyan active states, subtle grid/source texture, square-leaning rounded geometry, and restrained motion. It must remain calm enough for long reading sessions.

## First viewport

- A compact status strip identifies privacy mode and active tab count.
- Page content owns most of the screen.
- A four-action developer rail exposes Source, Text, Console, and Info with one tap.
- The address/navigation capsule anchors the bottom for thumb reach.

## Tokens

- Background `#090D12`; surface `#101720`; raised `#17212C`.
- Primary `#5DD6FF`; secondary `#8DF5C4`; warning `#FFC46B`; error `#FF7B8B`.
- Primary text `#EDF7FF`; secondary text `#9FB0C0`; separators `#263442`.
- Corners: 12dp controls, 18dp panels, 24dp bottom dock.
- Motion: 140–220ms state changes; no ornamental looping animation.

## Responsive behavior

- Phones use the bottom dock and compact quick-action rail.
- Wide screens cap readable content and may place developer tools in a side pane in their owning session.
- IME must not cover the address field; landscape keeps core actions visible.

## States

- Empty/new tab: useful local dashboard with address entry visible.
- Loading: thin deterministic/indeterminate page progress; keep content stable.
- Error/offline: plain cause, retry, and editable address.
- Success feedback: brief snackbar, never a blocking dialog.
