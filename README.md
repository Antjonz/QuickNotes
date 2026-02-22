# QuickNote

An Android note-taking app I built for myself. Everything is stored locally on your phone.

---

## What it does

### Notes
- When you open the app you see all your notes as cards showing the title, a preview of the text, and the date
- Tap a note to open and edit it
- Notes only save when you tap the **Save** button in the toolbar — going back without saving discards your changes
- Each note has a title and a body

### Folders
- You can create folders to group notes together
- Tap a folder to open it and see the notes inside
- Tap the + button to add a new note directly into that folder

### Adding notes and folders
- On the home screen, tap the + button at the bottom right
- A menu pops up asking if you want to make a new note or a new folder

### Deleting
- Every note and folder has a red delete button (trash icon)
- When you tap it, the app asks if you're sure before actually deleting anything
- Deleting a folder also deletes all the notes inside it

### Reordering
- You can drag notes and folders into whatever order you want by holding and dragging them using the handle icon (≡) on the left side
- The order is saved, so it stays the same next time you open the app

### Moving notes between folders
- On the home screen, drag a note onto a folder — the folder highlights purple when you're hovering over it
- Release while hovering to move the note into that folder, or drag away and release to cancel
- Inside a folder there is a **"↑ Drag here to move out of folder"** bar at the top of the list
- Drag a note up onto that bar — it highlights purple when hovering — release to move the note back to the home screen, or drag away to cancel

### Photos
- Inside a note you can add photos by tapping the image button at the bottom
- Photos you've added show up as small thumbnails in a row at the bottom of the note
- Tap the trash icon next to the add-photo button to enter remove mode — the ✕ badges appear on all thumbnails — tap one to remove that photo immediately
- Tap the trash icon again to leave remove mode and hide the badges
- Tap a thumbnail to open it full screen
- In the full-screen view you can swipe left and right to go through all the photos in that note
- Swipe a photo downward to close the full-screen view
- Long-press a photo in the strip and drag it to change the order

---

## Tech stuff

- Built with Kotlin for Android
- Uses Room for local database storage
- MVVM architecture (ViewModel + LiveData)
- Material 3 design
- Minimum Android version: Android 8.0 (API 26)

