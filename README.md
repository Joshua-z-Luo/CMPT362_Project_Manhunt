# CMPT 362 — Manhunt (Android Game)

Manhunt is a real-world, location-based tag game played outdoors with your phone. Players become **Hunters** or **Runners** and move around in the real world while watching a live map on their screens.

---

## 🧩 What Is Manhunt?

Think of it as **real-life tag + maps + special abilities**:

- 📍 You see yourself on a map using GPS
- 🧑‍🦯 You physically move around in the real world
- 🕵️ The Hunter chases and tries to “catch” the Runners
- 🏃 The Runners try to survive until the timer ends
- ✨ Both sides can use abilities to help them chase or escape

You’ll want at least **2 players** (1 Hunter, 1 Runner), but it’s more fun with a group.

---

## ✨ Main Features

- **Lobby system** — one person hosts, others join with a room code
- **Live map** — everyone's positions update on a shared map
- **Random Hunter selection** — exactly one Hunter per game
- **Global countdown** — everyone starts at the same time
- **Adjustable settings**, including:
    - Game duration
    - Hunter detection range
    - Runner detection range
    - Abilities on/off
- **Simple role-based screens:**
    - Hunter screen
    - Runner screen
    - “I Got Caught” button for Runners

---

## 🧰 What You Need

To run the game yourself, you’ll need:

- A computer with **Android Studio** installed
- At least one Android phone
- A **Google Maps API key**
- The backend server from this repo:  
  👉 https://github.com/Joshua-z-Luo/cmpt362-rooms-backend

---

## 📦 Getting the App Running

### 1️⃣ Download the Project APK/repo
- Install the app on any Android phone. The app will appear like any regular app.
- Clone this repo on to any computer running Android Studio. This is needed for app setup.

---

## 2️⃣ Set Up the Backend (One Time)

The app needs a small server running in the background to keep everyone in sync.

Backend project:  
👉 https://github.com/Joshua-z-Luo/cmpt362-rooms-backend

Follow the instructions in that README to:

- Deploy or run the backend
- Get the **Base URL** (looks like `https://something.workers.dev`)

You’ll paste this Base URL into the app later.

---

## 3️⃣ Add Your Google Maps Key

The game uses Google Maps to show player locations.

Steps:

1. Go to the **Google Cloud Console**
2. Create (or select) a project
3. Enable **Maps SDK for Android**
4. Create an **API key**
5. Copy the key

In Android Studio, open `AndroidManifest.xml` and find:

```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_API_KEY_HERE" />

```

Replace `YOUR_API_KEY_HERE` with your real key.

(If you’re unsure, ask someone for help — you only set this up once.)

---

## 4️⃣ Install the App on Phones


- Simply download the provided APK on the website

Everyone should now see the **Manhunt app icon**.

---

# 🕹️ How to Host & Play a Game

## Step 1 — Host Creates a Lobby

Choose one player to be the **Host**.

On the host’s phone:

- Open the app
- Paste the **Base URL**
- Enter a **Display Name**
- Tap **Create Lobby**

A room code like **ABC123** appears — share this with everyone.

---

## Step 2 — Other Players Join the Lobby

On every other player’s phone:

- Open the app
- Enter the same **Base URL**
- Enter a **Display Name**
- Type the **Room Code**
- Tap **Join Lobby**

You’ll see a **Members** list showing everyone.

---

## Step 3 — Adjust Game Settings (Host Only)

On the host’s phone, tap **Settings**.

You can configure:

- **Game Timer** — match length
- **Hunter’s Range** — how close a Runner must be to appear
- **Runner’s Range** — when the Hunter becomes visible
- **Ability Mode** — On/Off

When done, return to the lobby.

---

## Step 4 — Start the Game

When everyone is ready:

The Host taps **Start Game**.

The app:

- Randomly picks a **Hunter**
- Assigns others as **Runners**
- Starts a shared **30-second countdown**

Everyone sees:

> *Game starts in XX sec…*

When the countdown ends:

- The Hunter sees the **Hunter Map Screen**
- Runners see the **Runner Map Screen**

No extra tapping required.

---

# 🎯 What You See In-Game

## Hunter Screen

You’ll see:

- Live map of your position
- **Red detection circle**
- Runner markers (when in range)
- Countdown timer
- **Abilities** button

---

## Runner Screen

You’ll see:

- Live map of your position
- **Blue detection circle**
- Hunter marker (when close enough)
- Timer
- **Abilities** button
- **“I Got Caught!”** button

---

# 🧙 Abilities (High-Level)

Abilities add strategy and unpredictability.

## Hunter Abilities

- **Scan** — expands detection range
- **Reveal** — briefly shows all Runners
- **Tracker** — follow a specific Runner
- **Hunter Invisibility** — hides the Hunter temporarily

## Runner Abilities

- **Invisibility** — hide from the Hunter
- **Hidden** — harder for Hunter to detect
- **Stationary** — your marker fades when still
- **Shield** — prevents your first tag

Abilities:

- Are triggered through an **Abilities dialog**
- Last a short time
- Have cooldowns

---

# 🧵 What Happens When You’re Caught?

When a Runner gets tagged in real life:

- Tap **“I Got Caught!”**
- You’re moved to a **Caught** screen
- You are out for the match (or spectate — depends on your group)

**Win Conditions:**

- **Hunter Wins** → all Runners get caught
- **Runners Win** → at least one survives until the timer ends

---

# 🧪 Tips for a Smooth Game

Play in an **open, safe area** (campus, park, field).

Ensure:

- Everyone has **GPS** on
- Everyone uses the **same Base URL**
- Everyone entered the **same Room Code**
- Keep phones **charged** — GPS drains battery
- Agree on **real-life boundaries**

---

# 🛠️ Troubleshooting

### 🔴 Map is blank / grey
- Google Maps API key may be missing or invalid
- Make sure your device has internet

### 🔴 Players don’t appear
- Confirm **same Base URL**
- Confirm **same Room Code**
- Check internet + GPS permissions

### 🔴 Location doesn’t update
- Check Android location permissions
- Turn on **High Accuracy** mode

---

# 🙌 Credits
### Team Members
- Parth Patel
- Joshua Luo
- Kai Cho
- Rahul Naterwala
- Forest Wang

Inspired by:

- Real-world Manhunt
- Location-based games
- Asymmetrical multiplayer design

**Have fun, and stay safe! 🚀**

