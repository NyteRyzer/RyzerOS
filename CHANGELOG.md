# Changelog

Every version of Ryzer OS, newest first. Downloads for each one are on the [Releases page](https://github.com/NyteRyzer/RyzerOS/releases).

## 2.4.0: 20 new features

#### Library
1. **Multi-disc games show once.** Tracks listed in a `.cue` or `.gdi` file, and discs listed in an `.m3u`, no longer appear as separate games (PS1, Saturn, Dreamcast, Sega CD...).
2. **BIOS files are hidden.** Files like `scph1001.bin`, `gba_bios.bin` and `neogeo.zip` stay out of your game lists.
3. **Game details from your gamelists.** If you use ES-DE (or EmulationStation, Batocera, RetroBat), Ryzer reads its `gamelist.xml` files and shows each game's description, release year, developer, genre and players, in the game list, Game info and the Cover viewer.
4. **Library check-up** (*Settings → Library*): systems without an emulator, files Ryzer doesn't recognise, games without pictures, duplicates, empty folders, and what was tidied away.
5. **"Last played 3 days ago"** on every game, and *Last played* sorting now covers your whole history.
6. **Recent searches**: Y shows your last 6 searches.
7. **Export your library** (*Tools*): your games and every play session as CSV spreadsheets.

#### Dual screen and playing
8. **Choose the screen for each game** (game options → *Opens on*), on top of the per-system choice.
9. **Now Playing auto-dim**: the second screen goes dark after 30 s to 5 min without a touch. Tap to wake it.
10. **Battery time left**: an estimate on the Now Playing screen, worked out from how fast the battery is draining.
11. **Unplug reminder**: a notice when charging reaches 80, 85 or 90%, which helps the battery last longer.
12. **Cover viewer**: the box art full screen, with the game's details on the top screen.

#### Motivation
13. **Ryzer Wrapped** (*Tools*): your month or year in games, with total time, days played, best streak, top 5 games, favourite system, biggest day and longest session.
14. **Daily play goal**: a ring next to your name on the top screen fills up as you play, with a notice when you reach it. It also shows on the Now Playing screen.
15. **Weekly report**: every Monday, a notice with last week's play time and your most-played game.
16. **Streak reminder**: in the evening, if you have a streak going and haven't played yet.
17. **6 new milestones**: Night Owl, Early Bird, Explorer, Completionist, Centurion and Weekend Warrior. The Ryzer Legend bonus theme now needs all 20.
18. **Game Roulette filters**: spin through all games, never played, backlog, favorites or top rated (L / R).

#### Look
19. **Wallpaper slideshow**: cycles through `RyzerOS/wallpapers` every 1 to 60 minutes.
20. **High-contrast mode**: pure white (or black) text and strong outlines on any theme.

Plus: the *Goals & reminders* menu in Settings, and Play history now keeps your last 500 sessions.

## 2.3.1: smoother game launches on dual screens

Opening a single-screen game (GBA, SNES, PSP, PS1, N64, GameCube...) on a dual-screen handheld no longer stutters or flashes:

- **No more blank flash on the second screen.** Ryzer's top screen now stays up until the Now Playing panel has drawn behind it, then the two swap in the same moment the game starts.
- **No more hitch as the game starts.** Home used to rebuild all of its tiles at the exact moment the emulator was loading. It now waits until you come back. Looking games up is also much faster, which helps big libraries everywhere.
- **Faster GameCube, PS2, Wii and Vita launches.** Freeing memory used to happen *before* the game started, which could take a few seconds. It now happens while the emulator loads.
- **Artwork downloads pause while you play**, so they never compete with the emulator.
- Play data is saved in the background, and the launch animation is shorter and also shows on the top screen.

## 2.3.0: swap screens mid-game and automatic updates

### Swap screens while you play

On dual-screen handhelds, GBA, SNES, PSP, PS1, N64, GameCube and other single-screen games open on one screen with the Now Playing panel on the other. Now you can **swap them at any time without stopping the game**:

- Tap **⇅ Swap screens** at the top of the Now Playing panel, or **swipe up or down** on it.
- The game keeps running and keeps your controller. Only the screens trade places.
- Ryzer **remembers your choice for each system**, so if you move a GBA game to the top screen, GBA games open there from then on. Turn this off, or reset it, in *Settings → Display & dual screen*.
- Needs Android 10 or newer (the RG DS, AYN Thor, AYANEO Pocket DS and Retroid Dual all qualify).

### Automatic updates

- Ryzer checks for new versions once a day on Wi-Fi, downloads them in the background and asks before installing. Android checks that every update is signed with the same key as the Ryzer you have, so only genuine builds can install.
- The first update asks once for permission to install. On Android 12 and newer, later updates install without the extra system prompt.
- After updating, a *What's new* screen shows once. Your settings, themes and play history are kept.
- *Settings → Updates*: check now, turn automatic checks or downloads off, or skip a version.

## 2.2.0: automatic artwork

Ryzer now finds pictures for your games by itself.

- **Reads your ROMs to identify each game.** The CRC32 checksum (instant for `.zip` files) is looked up in the libretro database of No-Intro, Redump and FBNeo game lists. Even a file called `the_legend_of_zelda_minish_cap.gba` gets the right cover. Discs and large files are matched by name, with fuzzy matching and your region preferred.
- **Downloads the artwork** from the free libretro thumbnail library: no account and no API key. Pick **box art**, **screenshots** or **title screens**.
- **Automatic**: after each library scan, new games get their pictures in the background (Wi-Fi only by default). A notification says how many were found.
- **Or on demand**: *Settings → Library → Artwork → Download missing artwork*, *Get artwork for this list* in any game list's options, or *Find artwork online* for one game.
- A live progress screen shows each cover as it's found, with the newest one on the top screen.
- Pictures are saved small (about 30 KB each) in `RyzerOS/covers`. **Your ROM folders are never changed**, and art you already have (next to your ROMs, or from ES-DE) always wins.
- Works for 26 systems, from NES and Game Boy to PS2, GameCube, Wii, 3DS, Dreamcast and arcade (FBNeo).

## 2.1.1: no more duplicate games

- Fixed games showing up two or three times. Android's shared storage ignores letter case, so `ROMs`, `Roms` and `roms` were all scanned as separate folders.
- Home now starts on its first tile (Continue) instead of jumping to Favorites when the library finishes loading.
- New screenshots everywhere, a [website](https://nyteryzer.github.io/RyzerOS/) and a nicer releases page.

## 2.1.0: 30 new features

**Play**
1. **Snake**, a mini-game with saved high scores (Tools).
2. **2048**, the other mini-game, also with high scores.
3. **Game Roulette**: a slot-machine reel spins through your games and you play whichever it lands on.
4. **Launch animation**: the box art zooms in before the game starts.
5. **"Continue playing?"** at startup offers your last game, along with your notes for it.

**Library**
6. **For You**: recommendations from the systems you play most and the games you rate highest.
7. **Smart collections** that fill themselves: Now Playing, Backlog, Top Rated, Never Played and New This Week.
8. **NEW badges** on games added in the last 7 days.
9. **Search everything**: Y finds games, Android apps and Ryzer screens in one place.
10. **ROM check (CRC32)** in any game's options, to spot a damaged file.
11. **Per-system backgrounds**: `RyzerOS/backgrounds/gba.jpg` shows behind your GBA list.

**Look**
12. **Home tile editor**: choose which Home tiles appear.
13. **Icon shapes** for the 3DS layout: rounded, circle, square or hexagon.
14. **Neon mode**: glowing outlines on every panel.
15. **Screen transitions**: screens slide in as they open.
16. **Binary clock**, a new top-screen clock style.
17. **Word clock** ("quarter past seven"), another new clock style.
18. **4 new live wallpapers**: fireflies, leaves, hearts and matrix.
19. **Seasonal effects**: snow in winter, petals in spring, fireflies in summer, leaves in autumn and hearts on Valentine's Day.
20. **Photo-frame screensaver**, using your pictures in `RyzerOS/photos`.
21. **Custom boot message**.
22. **5 unlockable bonus themes** (Bronze, Silver, Gold, Platinum, Ryzer Legend) that you earn with milestones.

**Tools** (a new Home tile, toolbar button and Settings entry)
23. **Play calendar**: a month view of the days you played, shaded by how long.
24. **Share my stats**: makes a picture card of your stats and opens the share menu.
25. **Screenshots** of Ryzer, from the Tools or Quick menu.
26. **Stopwatch and timer**, with laps; the timer sends a notification when it finishes.
27. **Controller test**: every button, stick and trigger, live.
28. **Battery info**: charge, temperature, voltage, current and health.
29. **System brightness** control and a **bedtime reminder**.
30. **What's New** and **Help & tips** screens.

## 2.0.0: the 3DS-style redesign

- **New 3DS-style Home** (default): the top screen shows a big floating banner for the selected item, with your profile, the clock, the date and notifications. The bottom screen has a toolbar (Settings, Theme Studio, Notifications, Stats, Search, Swap, Quick menu, Profile) above a sideways-scrolling grid of icons. Ryzer's glow, colour bands and themes are all still there. The Ribbon and Grid layouts are one setting away (Personalize → Look & feel → Home layout).
- **Theme Studio**, a full designer:
  - all 12 colours, picked from a palette or fine-tuned by hue, saturation and brightness
  - 10 background patterns, plus the theme's own live wallpaper, picture, picture dimming and font
  - panel opacity (glass), tile style, corner roundness, text size and particles
  - a readability check with one-press auto-fix, random palettes, and copying another theme as a starting point
  - share codes that friends can import
- **Notification centre**: your Android notifications in Ryzer, with slide-down banners, an unread badge, open, dismiss, clear all and do not disturb. They also appear on the Now Playing screen while you play. Switch it on once under Settings → Notifications → START → Allow notification access.
- **Screen swap button**: L3 or R3 swaps the two screens instantly. You can also set any other button (Settings → Display & dual screen → Swap button) or use the toolbar or quick menu.
- **Live wallpapers**: aurora, starfield, bubbles, rain, snow and waves, tinted by your theme, with adjustable speed.
- **Profile**: your name and picture on the top screen and toolbar.
- **More menus**: Personalize, Notifications, Profile and Notification settings, plus swap and do not disturb in the quick menu.

## 1.2.0: dual-screen play and photo themes

### Now Playing on the second screen

On dual-screen handhelds (RG DS, AYN Thor, AYANEO Pocket DS, Retroid Dual), **single-screen systems** such as GBA, SNES, PSP, PS1, N64 and GameCube open the game on one screen. The other screen becomes a **Now Playing** touch panel showing:
- box art, title, system and emulator
- a live session timer, total play time and time left under your daily limit
- the clock and battery
- touch controls: volume, mute, favorite, rating, status, emulator controls, your notes, break reminder, screen off, and back to Ryzer

DS and 3DS games still get both screens. If games open on the wrong screen, switch **Settings → Display & dual screen → Single-screen games open on**.

### 20 more features

1. **Photo themes**: pick any picture from your gallery and Ryzer builds a full theme from its colours, with the photo as the background (Themes → START, or Look & feel)
2. **Glass panels**: see-through panels over wallpapers and photo themes
3. **Box-art colours**: panels take on the colours of the selected game's art
4. **System colours**: the accent colour matches the system you're on
5. **RGB mode**: slowly cycling rainbow accents
6. **Night light**: a warm filter for night-time play
7. **Custom system tiles**: your own picture for any system (`RyzerOS/tiles/gba.png`)
8. **Grid Home layout** as an alternative to the console ribbon
9. **Menu music** from your own songs in `RyzerOS/music`, paused during games
10. **Sound packs**: Classic, Chiptune or Soft
11. **Greeting with your name** on the top screen
12. **Game of the Day** tile, favouring games you haven't played
13. **Pin games to Home**
14. **Session history**: your last 100 sessions in Stats
15. **Game notes** (codes, where you left off), also shown on Now Playing
16. **Break reminder** on the Now Playing screen
17. **Daily play limit**, enforced in kid mode
18. **Emulator controls cheat sheets** (how to open each emulator's menu and save states; editable in `RyzerOS/hotkeys.json`)
19. **Swipe gestures** on the touch screens
20. **Boot animation styles**: Classic, Glitch or Minimal

Also: a **recently played shelf** on the top screen.

## 1.1.1: games launch again

- Fixed "*emulator* refused to launch" when opening games in PPSSPP, DraStic, Azahar and others. Ryzer now hands ROMs to emulators the way each one expects, tries several methods, and saves the reason to `RyzerOS/crash.txt` if all of them fail.
- New setting: **Settings → Emulators → How games are opened**.

## 1.1.0: crash fix and stable downloads

- Fixed a crash on start on some devices (including the RG DS).
- Added a crash reporter: if Ryzer closes, the error is shown on the next start and saved to `RyzerOS/crash.txt`.
- Releases now always have the same file names, so `releases/latest/download/RyzerOS.apk` always gets the newest version.

## 1.0.0: first release

The Android edition for stock-OS handhelds, with Android 6+ support. It included:

**Library**
1. **Collections**: make your own lists (e.g. "Couch co-op"), each with its own Home tile
2. **Hide games** you don't want to see, plus a "Show hidden games" switch
3. **Per-game emulator**: pick a different emulator for one game
4. **Sort** by name, last played, most played, rating, file size or recently added
5. **Filter** by favorites, never played, or completion status
6. **Rename games** (a display name only; the file is untouched)
7. **Star ratings** from 1 to 5
8. **Completion status**: Backlog, Playing, Beaten or Completed
9. **Most Played** list and Home tile
10. **Recently Added** list and Home tile
11. **Random pick** from any list (a system, a collection or favorites)
12. **Grid view** with box art (press SELECT in any game list)
13. **Duplicate finder** for the same game found more than once
14. **Storage by system**: how much space each system's games use
15. **Delete ROM** from the game's options, with a confirmation

**Stats**
16. **Play stats**: total time, launches and your top games
17. **7-day activity chart**
18. **Day streak** counter
19. **14 milestones** to unlock (e.g. "Marathon": 3 hours in one day)
20. **Session summary** after each game ("You played Kirby for 42m")

**Look & feel**
21. **Screensaver**: drifting box art and a clock, with the screen dimmed
22. **Wallpapers**: put images in `RyzerOS/wallpapers`
23. **Theme of the day** or a **random theme** on every start
24. **Day / night themes** that switch automatically
25. **5 fonts** and **3 interface sizes**
26. **Home layout**: order systems (A-Z, most games, recently played) and hide the ones you don't use

**Device**
27. **Quick menu (L + R)** from anywhere: volume, mute, brightness, Wi-Fi, Bluetooth, free memory
28. **Vibration** on button presses
29. **Battery saver**: lighter menus below 20%, with warnings at 15% and 5%
30. **Kid mode**: hides settings, Apps and file actions behind a PIN

Also included: settings **backup and restore**, an **analog clock** style for the top screen, and **store links** for missing emulators.

The Linux edition (ROCKNIX, Knulli, muOS, ArkOS) shipped alongside it. See [docs/LINUX.md](docs/LINUX.md).
