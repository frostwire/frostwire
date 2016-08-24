<a href="http://frostwire.com"><img src="http://biz.prlog.org/frostwire/logo.png"></a>


[![tip for next commit](http://tip4commit.com/projects/538.svg)](http://tip4commit.com/projects/538)


# Hi there FrostWire hacker!

FrostWire is a file sharing client and media management tool that was made using
lots of cool open source projects. It was born from the legendary *LimeWire
Gnutella* client, but it's evolved a hell of a lot since then.

FrostWire no longer supports Gnutella,** it's a BitTorrent client, an Internet
Radio client and Media Player.**

Unlike most BitTorrent clients out there, FrostWire focuses on searching files
and tries hard to make it as easy and convenient as possible to users.

Old FrostWire users were used to the Gnutella experience (searching for single
files), so FrostWire makes use of BitTorrent a little differently to make it
simple for them.

FrostWire will connect to all the major BitTorrent indexes of the internet and
pre-fetch torrents (via the libtorrent DHT or via HTTP if it can't find it on the
DHT), it will then index locally all the available metadata that's indexed by
the torrent file, as the user searches, the local index gets better and better
to yield richer and instant results.

This makes FrostWire a very powerful client that will help you find the rarest
of files on the bittorrent network, sometimes it will find files that even the
best BitTorrent indexes won't yield in the search results.

The main software architecture (how things are organized) depends on the late
*LimeWire 4,* but it has evolved through FrostWire 5, and now the source code
layout has been simplified enourmously for FrostWire 6.

- The BitTorrent power comes from the [frostwire-jlibtorrent](https://github.com/frostwire/frostwire-jlibtorrent) library.
- Media playback comes from the [MPlayer](http://www.mplayerhq.hu/design7/news.html) project
- The good looks and skinning system comes from the Substance skinning project (which we've had to maintain on our repo to make it fit FrostWire needs)
- HTTP interaction comes from the [Apache Commons](http://commons.apache.org/) project
- The search is built using the awesome [H2 database](http://www.h2database.com/html/main.html) and [Lucene indexes](http://lucene.apache.org/core/).
- JSON parsing comes from [google-gson](https://code.google.com/p/google-gson/), and so on and so on.

# Installers

If you're just looking for a FrostWire installer here are the latest official builds for [Windows, MacOSX, Linux](http://www.frostwire.com/downloads) and [Android](http://www.frostwire.com/android)

# Build Requirements

Introductions aside, here's how you build this.

Pre-requisites:

* JDK 1.7 or later ([OpenJDK](http://openjdk.java.net/) or [Oracle JDK](http://www.oracle.com/technetwork/java/index.html))
* [Apache Ant](http://ant.apache.org/)
* [Gradle](http://www.gradle.org/)
* [Git](http://www.git-scm.com/) to clone, check out the project to your machine.

Make sure your `CLASSPATH`, `JAVA_HOME` and your `PATH` environment variables are set correctly.

Example on a Ubuntu system's .bashrc file:

    JAVA_HOME=/usr/lib/jvm/java-7-sun
    CLASSPATH=${CLASSPATH}:${JAVA_HOME}/lib
    PATH=${PATH}:${JAVA_HOME}/bin
    export JAVA_HOME CLASSPATH PATH

Most build problems are usually solved by having those environment variables set
correctly. If you are a Windows or Mac user the process is fairly similar.

We recommend using [IntelliJ Idea](http://www.jetbrains.com/idea/) as your development environment.

# Git cloning instructions

We recommend you first clone the repository to your machine from the `origin` repository at https://github.com/frostwire/frostwire

```
git clone https://github.com/frostwire/frostwire
cd frostwire/desktop
```

**If you plan on hacking FrostWire**, fork the repository on github, and then add your fork's uri as a remote

`git remote add myGithubAccount https://github.com/myGithubAccount/frostwire`

If you issue a `git remote -v` it should look something like this (where `myGithubAccount` is your user account):
```
$ git remote -v
origin          https://github.com/frostwire/frostwire (fetch)
origin          https://github.com/frostwire/frostwire (push)
myGithubAccount https://github.com/myGithubAccount/frostwire (fetch)
myGithubAccount https://github.com/myGithubAccount/frostwire (push)
```

# Build Instructions

Be sure that you're in the project root, else then execute:

    cd frostwire/desktop
    gradle clean build

Then run the project with:

    ./run
    
If you are using Windows, run the project with `run.bat` in the `src` folder.

# HAVING ISSUES BUILDING?

"My environment variables are fine, my requirements are met, there's an error during the build."*

It's very hard that it happens but we might have pushed out a broken build.

If you do have any issues building, please yell on the comments of the
offending commit log at github.com so we can address the issue right away.

If the build is not broken, hit us up at the [Developer Forum](http://forum.frostwire.com/)


# Code Organization

| Location        | Contains    |
| ------------- |:-------------:|
| **src/com/frostwire/**     | Search, mp3 parsing, Json Engine, mplayer integration, bittorrent, . |
| **src/com/limegroup/gnutella/gui/**      | Everything the user sees on screen is here.Like Java Swing? this is  probably a great place to learn more about it. If you're going to be adding new UI elements make sure you put them inside **com.frostwire.gui.\*** (Most of the stuff on **com.limewire.gui** are legacy code from LimeWire) Good starting points to see how it all works are the **\*Mediator.java** files. Being the McDaddy **GUIMediator.java** |
| **resources/** | This is where most graphical assets are stored.      |
| **lib/jars** | This is where we keep pre-compiled jars from projects we don't maintain.     |
| **lib/jars-src** | This is where we keep the sources of those third party projects. We do this because we hope one day we'll be accepted into  debian or ubuntu and it's a requirement that your packages  can be compiled without any binary dependency. This also helps us help those projects, sometimes we fix bugs that affect us and we send patches back to those projects.  Also on eclipse it's awesome to be able to browse the source  of those dependencies and to step-by-step debug to see what  the hell those developers were thinking.|
| **lib/messagebundles**  |   Where we keep the translation files. |
| **lib/icons**  |  Where we keep the FrostWire launcher icons for the differentoperating systems. |
| **splashes/**  |  Where we keep all the splash screens for each major version of FrostWire. There are tools there to build the splash.jar and to build a collage of pictures with all the splashes for a release. |


# Coding Guidelines

[5 Object Oriented Programming Principles learned during the last 15 years][3]
[3]: <http://bit.ly/y0hdR4>

* Keep it simple. ([KISS](https://en.wikipedia.org/wiki/KISS_principle))
* Do not repeat yourself. ([DRY](https://en.wikipedia.org/wiki/Don%27t_repeat_yourself)) Re-use your own code and our code. It'll be faster to code, and easier to maintain.
* If you want to help, the [Issue tracker](https://github.com/frostwire/frostwire/issues) is a good place to take a look at.
* Try to follow our coding style and formatting before submitting a patch.
* **All pull requests should come from a feature branch created on your git fork**. We'll review your code and will only merge it to the master branch if it doesn't break the build. If you can include tests for your pull request you get extra bonus points ;)
* When you submit a pull request try to explain what issue you're fixing in detail and how you're fixing in detail it so it's easier for us to read your patches. If it's too hard to explain what you're doing, you're probably making things more complex than they already are. Look and test your code well before submitting patches.
* We prefer well named methods and code re-usability than a lot of comments. Code should be self-explanatory.


# Contribution Guidelines

Every countribution merged to the master branch will automatically receive a tip of 1% of whatever funds are available on the [tip4commit fund](https://tip4commit.com/github/frostwire/frostwire-android).

Tip for next commit: [![see here](http://tip4commit.com/projects/538.svg)](http://tip4commit.com/projects/538)

If you want to contribute code, start by looking at the [open issues on github.com](https://github.com/frostwire/frostwire/issues).

If you want to fix a new issue that's not listed there, create the issue, see if
we can discuss a solution.

Please follow the following procedure when creating features to avoid unnecessary rejections:

* Fork the source.
* Clone it locally
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

    git checkout master
    git pull upstream master

and then rebase your branch to bring it up to speed so it can be merged properly (do not merge `master` into your branch):

    git checkout my-branch
    git rebase master

As you do this you may have to fix any possible conflicts, just follow the instruction git gives you if this is your first time.

Make sure to squash any cosmetic commits into the body of your work so that we don't pollude the history and you don't get more bitcoins than you should from the rest of the collaborators for things like fixing a typo you just introduced on your branch.

_Repeat and rinse, if you send enough patches to demonstrate you have a good
coding skills, we'll just give you commit access on the real repo and you will
be part of the development team._

### Environment Variables for Runtime Configuration

 **FROSTWIRE_FORCE_UPDATE_MESSAGE** if this env variable is set FrostWire will always show the update dialog for testing purposes

### License

Frostwire Desktop is offered under the [GNU General Public License](http://www.gnu.org/copyleft/gpl.html). Available [here](LICENSE.txt).


### Official FrostWire sites

[Main Website Frostwire.com](http://www.frostwire.com) |
[Frostwire Forum](http://forum.frostwire.com) |
[Facebook](http://www.facebook.com/FrostWireOfficial) |
[Twitter @frostwire](https://twitter.com/frostwire) |
[Tumblr](http://tumblr.frostwire.com)

[Git Repository at GitHub.com](https://github.com/frostwire/frostwire.git)
