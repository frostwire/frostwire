# The FrostWire Monorepo

Welcome to the main FrostWire repository. 
Here you will find the sources necessary to build [FrostWire for Android](http://www.frostwire.com/android/?from=github) and [FrostWire for Desktop](http://www.frostwire.com/downloads/?from=github)

 * [/android](https://github.com/frostwire/frostwire/tree/master/android) Sources for FrostWire for Android
 * [/desktop](https://github.com/frostwire/frostwire/tree/master/desktop) Sources for FrostWire for Desktop (Windows, Mac, Linux)
 * [/common](https://github.com/frostwire/frostwire/tree/master/common) Common sources for the desktop and android client

# Coding Guidelines

[5 Object Oriented Programming Principles learned during the last 15 years](https://www.gubatron.com/blog/5-object-oriented-programming-principles-learned-during-the-last-15-years/)

* Keep it simple, stupid. ([KISS](https://en.wikipedia.org/wiki/KISS_principle))
* Do not repeat yourself. ([DRY](https://en.wikipedia.org/wiki/Don%27t_repeat_yourself)) Re-use your own code and our code. It'll be faster to code, and easier to maintain.
* If you want to help, the [Issue tracker](https://github.com/frostwire/frostwire/issues) is a good place to take a look at.
* Try to follow our coding style and formatting before submitting a patch.
* **All pull requests should come from a feature branch created on your git fork**. We'll review your code and will only merge it to the master branch if it doesn't break the build. If you can include tests for your pull request you get extra bonus points ;)
* When you submit a pull request try to explain what issue you're fixing in detail and how you're fixing in detail it so it's easier for us to read your patches. If it's too hard to explain what you're doing, you're probably making things more complex than they already are. Look and test your code well before submitting patches.
* We prefer well named methods and code re-usability than a lot of comments. Code should be self-explanatory.


# Contribution Guidelines

If you want to contribute code, start by looking at the [open issues on github.com](https://github.com/frostwire/frostwire/issues).

If you want to fix a new issue that's not listed there, create the issue, see if
we can discuss a solution.

Please follow the following procedure when creating features to avoid unnecessary rejections:

Do this the first time (Cloning & Forking):
* Clone https://github.com/frostwire/frostwire to your computer. This will be the `origin` repo. 
```bash
git clone https://github.com/frostwire/frostwire
```
* Make a Fork of the `origin` repo into your github account.
* On your local copy, add your fork as a remote under your username as the remote alias.
```bash
cd frostwire
git remote add your_github_username_here https://github.com/your_github_username_here/frostwire
```

For further contributions
* Create a branch with a descriptive name of the issue you are solving.
* Make sure the name of your feature branch describes what you're trying to fix. If you don't know what to name it and there's an issue created for it, name your branch issue-233 (where 233 would be the number of the issue you're fixing).
* Focus on your patch, do not waste time re-formatting code too much as it makes it hard
  to review the actual fix. Good patches will be rejected if there's too much code formatting
  noise, we are a very small team and we can't waste too much time reviewing if something
  got lost or added in the middle of hundreds of lines that got shifted.
* Code, Commit, Push, Code, Commit, Push, until the feature is fully implemented.
* If you can add tests to demonstrate the issue and the fix, even better.
* Submit a pull request that's as descriptive as possible. Adding (issue #233) to the commit message or in PR comments automatically references them on the issue tracker.
* We'll code review you, maybe ask you for some more changes, and after we've tested it we'll merge your changes.

If your branch has taken a while to be accepted for merging into `master`, it's very likely that the `master` branch will have moved forward while you work. In this case, make sure to sync your `master`.

    git fetch upstream master

and then rebase your branch to bring it up to speed so it can be merged properly (do not merge `master` into your branch):

    git checkout my-branch
    git rebase origin/master

As you do this you may have to fix any possible conflicts, just follow the instruction git gives you if this is your first time.

Make sure to squash any cosmetic commits into the body of your work so that we don't pollute the history.

_Repeat and rinse, if you send enough patches to demonstrate you have a good
coding skills, we'll just give you commit access on the real repo and you will
be part of the development team._

# How to build

To build you will need the [Java Developer Kit 19](https://jdk.java.net/19/)

### Desktop
Go inside the [`desktop`](/desktop) directory and follow the build instructions in the [README.md](/desktop/README.md) file.


**Additional Desktop requirements**

***gettext***

If you want to work with the translation (i18n) bundles, you will need to install `gettext` to perform the text extraction and bundling tasks (`gradle gettextExtract`, `gradle gettextBundle`)

If you are on Mac, `gettext` installation is very simple with brew: 

`brew install gettext`.

If you are on Ubuntu, `gettext` installation can be done with 

`sudo apt install gettext`.


***Windows developers***

If you are developing in Windows we recommend you work with [MinGW](https://sourceforge.net/projects/mingw/files/) and install the `gettext` package. 

We also recommend you use [git for window's terminal](https://git-scm.com/download/win) instead of `cmd.exe`. All of our scripts will work as if you were working in Linux/Mac. Git's terminal supports window resizing, more convenient copying and pasting, `Tab` text completion, `Ctrl+R` reverse search, common bash keyboard shortcuts and basic GNU tools right out of the box.

### Android
Build with Android studio or go inside the `android` directory and type:
`./gradlew assembleDebug`, debug builds will be created inside the `android/build` folder.


### IceBridge Distributed Search

FrostWire includes **IceBridge**, a standalone relay servent that provides reliable, authenticated, fragmented mesh transport between FrostWire peers for distributed search.

#### What it does

- Enables peer-to-peer search without a central index server
- Each FrostWire instance runs a local IceBridge daemon (in-process on desktop, separate JAR for testing)
- Daemons communicate over **rUDP** (Reliable UDP) — a custom protocol built on Netty with Ed25519-authenticated handshakes, ACK/retransmission, and fragmentation/reassembly for payloads exceeding the UDP MTU
- The desktop client talks to its local daemon over a **localhost HTTP control API**
- Search requests are signed with the peer's Ed25519 key; responses are verified by the requester

#### Architecture

```
FrostWire Desktop ──HTTP──▶ Local IceBridge Daemon ──rUDP──▶ Remote IceBridge Daemon
     (search UI)              (/send, /poll)                   (processes request,
                                                               queries local index,
                                                               signs response)
```

#### Building the standalone IceBridge JAR

```bash
cd desktop
./gradlew icebridgeJar
# Output: desktop/build/libs/icebridge.jar
```

#### Running IceBridge standalone

```bash
java -jar icebridge.jar \
  --rudp-port 6889 \
  --control-http-port 50066 \
  --role BOTH \
  --host 127.0.0.1 \
  --identity-file ~/.frostwire/icebridge-identity.dat
```

On startup, IceBridge prints `ICEBRIDGE_AUTH_TOKEN=<hex>` to stdout. All control API endpoints (except `/health`) require this token in the `X-IceBridge-Token` header.

#### Control API endpoints

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/health` | GET | No | Health check (`{"ok":true,"data":"ok"}`) |
| `/register` | POST | Yes | Ed25519-signed peer registration |
| `/route` | POST | Yes | Localhost-trusted peer routing (no signature required) |
| `/lookup` | GET | Yes | Look up a peer by Ed25519 public key |
| `/send` | POST | Yes | Send an opaque payload to a remote peer via rUDP |
| `/poll` | GET | Yes | Drain inbound messages |
| `/metrics` | GET | Yes | Runtime metrics (packet counts, registry size, etc.) |

#### rUDP protocol

The rUDP layer implements:

- **HELLO** — Ed25519-authenticated handshake with timestamp replay prevention (300s skew window)
- **DATA** — Sequence-numbered reliable delivery with ACK and retransmission (500ms interval, 5 retries, 5s timeout)
- **DATA_FRAG / DATA_END** — Fragmentation for payloads >1024 bytes (12-byte fragment headers, max 4096 fragments per group, max 16MB assembled)
- **HOLE_PUNCH** — NAT traversal (requires authenticated session)
- **RELAY** — Forwarding through forwarder peers (source identity verified)

Sessions are capped at 256 per daemon. Incoming search requests are rate-limited to 30 requests/minute per source.

#### File-level search

FrostWire indexes both torrent names and individual file paths within torrents using SQLite FTS5 with BM25 ranking. This means searching for "readme.txt" will find torrents containing that file even if the torrent name doesn't include "readme". Results that match on a file path surface the matched filename in the search results.

#### Identity management

Each FrostWire node has a cryptographic identity consisting of an Ed25519 keypair (signing) and an X25519 keypair (Diffie-Hellman). The identity's node ID is `SHA-1(ed25519PubRaw)` with a configurable proof-of-work difficulty (default 20 leading zero bits) to prevent Sybil attacks.

Identity generation uses jlibtorrent's native Ed25519 implementation for performance — the JDK's pure-Java Ed25519 is 50-100x slower on some platforms.

Identities can be backed up and restored using a **BIP39 24-word mnemonic phrase** (`com.frostwire.crypto.Bip39Mnemonic`). The 32-byte Ed25519 seed is encoded as a mnemonic with a SHA-256 checksum, enabling offline paper backup. `IdentityKeys.fromSeed(seed)` deterministically reconstructs the Ed25519 keypair from the mnemonic.

The desktop client exposes identity management via **Options > Identity**, showing the node ID, fingerprint, public key, difficulty, karma score, and shared torrent count, with buttons to show the seed phrase, restore from seed phrase, and export/import identity files.

#### Key source locations

| Component | Location |
|-----------|----------|
| IceBridge server | `common/src/main/java/com/frostwire/search/relay/icebridge/` |
| rUDP transport | `common/src/main/java/com/frostwire/search/relay/icebridge/udp/` |
| Control API | `common/src/main/java/com/frostwire/search/relay/icebridge/control/` |
| Desktop client + launcher | `desktop/src/main/java/com/frostwire/search/relay/icebridge/client/` |
| Local index (SQLite + FTS5) | `desktop/src/main/java/com/frostwire/search/relay/LocalIndexTable.java` |
| Distributed search performer | `common/src/main/java/com/frostwire/search/relay/DistributedSearchPerformer.java` |
| BIP39 mnemonic | `common/src/main/java/com/frostwire/crypto/Bip39Mnemonic.java` |
| Identity keys | `common/src/main/java/com/frostwire/search/relay/IdentityKeys.java` |
| Identity settings UI | `desktop/src/main/java/com/limegroup/gnutella/gui/options/panes/IdentitySettingsPaneItem.java` |
| Tests | `desktop/src/test/java/com/frostwire/search/relay/` |


### License

Frostwire Desktop and Frostwire Android are offered under the [GNU General Public License Version 3 (GPL 3.0)](https://www.gnu.org/licenses/gpl-3.0.txt).

### Other important FrostWire repositories
[JLibTorrent](https://github.com/frostwire/frostwire-jlibtorrent) | 
[Telluride Cloud Video Downloader](https://github.com/frostwire/telluride) | 
[FrostWire JMplayer](https://github.com/frostwire/frostwire-jmplayer)

### Official FrostWire sites

[Main Website Frostwire.com](https://www.frostwire.com) | 
[X @frostwire](https://x.com/frostwire) | 
[Reddit](https://reddit.com/r/frostwire) | 
[Facebook](https://www.facebook.com/FrostWireOfficial) | 
[Discord Chatrooms](https://discord.com/channels/461752211802947585/461752211802947587)


### SourceForge Mirror
[![Download FrostWire](https://img.shields.io/sourceforge/dt/frostwire.svg)](https://sourceforge.net/projects/frostwire/files/latest/download) [![Download FrostWire](https://img.shields.io/sourceforge/dd/frostwire.svg)](https://sourceforge.net/projects/frostwire/files/latest/download)
