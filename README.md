# Antigravity Launcher for PhpStorm

Adds an `Antigravity` action to PhpStorm 2026.1's main toolbar and Tools menu.

When clicked, it opens a new Terminal tab named `Antigravity` and runs:

```bash
agy
```

## Features

- Runs `agy` inside a normal shell session, so exiting Antigravity returns to the terminal prompt.
- Reuses the existing `Antigravity` tab and inserts the current editor file path when Antigravity is already running.

## Build

Download the latest plugin ZIP from [GitHub Releases](https://github.com/f0ska/antigravity-phpstorm-launcher/releases/latest/download/antigravity-phpstorm-launcher.zip).

Use Java 21 JDK and Gradle, then run:

```bash
./gradlew buildPlugin
```

The installable plugin ZIP will be created under `build/distributions/`.

On Ubuntu, if `javac` is missing, install the JDK package first:

```bash
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk
```

## Install

In PhpStorm:

1. Open `Settings > Plugins`.
2. Click the gear icon.
3. Choose `Install Plugin from Disk...`.
4. Select the ZIP from `build/distributions/`.
5. Restart PhpStorm.

If the button is not visible in the exact spot you want, right-click the main toolbar and use `Add Action to Main Toolbar`, then search for `Antigravity`.
