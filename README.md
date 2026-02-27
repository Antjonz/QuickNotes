# QuickNote

An Android note-taking app where you can create notes, lists, whiteboards and folders — all in one place. Everything is stored locally on your phone, nothing goes to the cloud.

---

## Screenshots

<div align="center">
  <img src="screenshots/01.png" height="480" style="margin-right:8px;" />
  <img src="screenshots/02.png" height="480" style="margin-right:8px;" />
  <img src="screenshots/03.png" height="480" style="margin-right:8px;" />
  <img src="screenshots/04.png" height="480" style="margin-right:8px;" />
  <img src="screenshots/05.png" height="480" style="margin-right:8px;" />
  <img src="screenshots/06.png" height="480" />
</div>

---

## What it does

### Notes
- When you open the app you see all your notes, lists, whiteboards and folders as cards
- Each card shows the title, a preview of the text, and the date
- Tap a note to open and edit it
- Notes only save when you tap the **Save** button — going back without saving shows a warning and discards your changes
- Each note has a title and a body

### Lists
- A list is like a to-do list — you give it a name and then add items to it
- Every item gets a number based on its position
- Check off an item and it slides down to the bottom of the list; uncheck it and it slides back to where it was
- Drag items using the handle on the left to reorder them — numbers update automatically
- Long items grow the row to show all the text

### Whiteboards
- A blank canvas you can draw on with your finger
- Tools at the bottom: colour picker, pen size slider, eraser, and a clear-board button
- **Pan** with two fingers, **zoom** with a pinch
- Tap **Save** in the top bar to save; going back without saving shows a warning

### Folders
- Create folders to group things together — you can put folders inside folders
- Tap a folder to open it and see what's inside
- Folders are shown in purple so they're easy to spot at a glance

### Item types at a glance
Each card has a coloured left stripe so you always know what you're looking at:
- 🟢 **Note** — teal stripe
- 🔵 **List** — blue stripe
- 🟣 **Whiteboard** — purple stripe
- **Folder** — purple card (no stripe)
- **Divider** — a thin horizontal line with an optional label; drag it just like any other item to organize your screen

### Custom icons
- Tap the small square icon on any note, list, whiteboard or folder card
- A popup asks whether you want to use **a picture**, **a solid color** (with rounded corners), or **reset to the default icon**
- For notes, lists and whiteboards you can also **change the label color** from the same popup
- The icon button on the card immediately updates to show the chosen image or color

### Label colors
- Tap the icon on any note, list or whiteboard card and choose **Change label color**
- Pick a color to tint the whole card background — the colored left stripe is hidden when a custom color is set and the title turns black for readability
- Pick the white swatch to reset to the default white card

### Text formatting in notes
The formatting toolbar appears between the title and the body when editing a note:

| Button | What it does |
|--------|-------------|
| **B** | Bold (toggle) |
| **I** | Italic (toggle) |
| **U** | Underline (toggle) |
| **D / S / N / L / H** | Font size — Default / Small / Normal / Large / Huge |
| **A** (color bar) | Letter color — choose from a palette; button background shows the active color |
| **A** (highlight bar) | Text highlight / background color |
| **✓** (icon button) | Special characters — hold to pick from a list (✓ ✗ = € → ★ • … ° ½ etc.); tap to insert the last chosen one; the button updates to show the current character |
| **↹** | Tab — inserts a 6-space indent at the cursor; press backspace to remove the whole tab in one go; pressing Enter on a tabbed line continues the indent on the next line |

- Toggle buttons (B / I / U) turn black with a white icon when active
- All formatting is preserved exactly when you save and reopen a note, and when you export/import

### Adding things
- Tap the **+** button at the bottom right of any screen
- A menu pops up: New note, New list, New whiteboard, New folder, or **New divider**
- Inside a folder the same menu appears
- A divider is a purely visual separator — give it a name or leave it blank

### Deleting
- Every card has a red delete button (trash icon)
- Tapping it asks for confirmation before deleting
- Deleting a folder with content inside asks whether you want to delete the contents or move them out first

### Reordering
- Hold anywhere on a card and drag to reorder
- The order is saved between sessions

### Moving things into folders
- On the home screen, drag any item — as you hover your finger directly over a folder the dragged card gets darker to show it's about to be moved inside
- Release to move it in, or drag somewhere else and release to just reorder
- Inside a folder there is a **"↑ Move out of folder"** bar at the top — drag an item onto it to move it back out
- At the bottom there is a **"Cancel"** zone — drag there and release to cancel the move

### Photos (in notes)
- Tap the image button inside a note to add photos
- Photos appear as small thumbnails in a strip at the bottom
- Tap a thumbnail to view it full screen; swipe left/right to browse, swipe down to close
- Tap the trash icon next to the add-photo button to enter remove mode — ✕ badges appear; tap one to remove that photo without a confirmation prompt
- Long-press a thumbnail and drag to reorder photos

### Export / Import
- Tap the **save icon** in the top-right corner of the home screen
- Choose **Export** or **Import**

**Export:**
- If there's nothing to export, you'll see a warning
- If there is data, the app shows you the file size without pictures and with pictures
- Choose whether to include pictures or not, then pick where to save the `.qnbackup` file
- The backup includes everything: notes (with full rich text formatting), lists, whiteboards, folders, dividers, custom icons (pictures or solid colors), and label colors

**Import:**
- Choose whether to delete your current data first or keep it and merge
- Confirm, then pick a `.qnbackup` file
- Everything is restored — folders, notes, lists, whiteboards, dividers, photos, icons and label colors
- Backups from older versions of the app are still supported

---

## Installing the debug APK

If you just want to try the app without building it yourself:

1. Download `app-debug.apk` from the repository (found in `app/build/outputs/apk/debug/`)
2. On your Android phone go to **Settings → Apps → Special app access → Install unknown apps**
3. Find your browser or file manager and enable **Allow from this source**
4. Open the APK file on your phone and tap **Install**
5. The app appears as **QuickNote** on your home screen

> The debug APK is signed with a standard Android debug key — it's fine for personal use but not for publishing to the Play Store.

---

## Tech stuff

- Built with Kotlin for Android
- Uses Room for local database storage
- MVVM architecture (ViewModel + LiveData)
- Material 3 design
- Rich text serialized as compact JSON (bold, italic, underline, font size, text color, highlight color)
- Backup/restore uses plain JSON (no external dependencies)
- Minimum Android version: Android 8.0 (API 26)
