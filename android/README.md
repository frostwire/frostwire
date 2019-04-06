# FrostWire for Android

**A file sharing client, media player and simple file manager for your Android devices.**

FrostWire for Android lets you search and download files from the BitTorrent network and several popular cloud services.

All the BitTorrent functionality is done using FrostWire's [jlibtorrent](https://github.com/frostwire/frostwire-jlibtorrent) library, a set of high level [libtorrent](https://github.com/arvidn/libtorrent) wrappers.

The FrostWire team is an active contributor to libtorrent and jlibtorrent always tries to stay on par with the latest libtorrent release.

![](https://i.imgur.com/U20h8cL.png)
- - -
# Coding guidelines

- **Keep it simple**.

- **Do not repeat yourself**. Re-use your own code and our code. It'll be faster to code, and easier to maintain.

- If you want to help, [Issue tracker](https://github.com/frostwire/frostwire-android/issues) is a good place to take a look at.

- Try to follow our coding style and formatting before submitting a patch.
 
- All pull requests should come from a feature branch created on your git fork. We'll review your code and will only merge it to the `master` branch if it doesn't break the build. If you can include tests for your pull request you get extra bonus points ;)

- When you submit a pull request, try to explain what issue you're fixing in detail and how you're fixing it in detail, so it's easier for us to read your patches.
  If it's too hard to explain what you're doing, you're probably making things more complex than they already are.
  Look and test your code well before submitting patches.

- We prefer well named methods and code re-usability than a lot of comments. Code should be self-explanatory.

Becoming a core collaborator with direct commit access to the upstream repository will only happen after we have received lots of great patches and we get to know you better.


# Submitting Pull Requests

- Fork the project.

- No matter how small your change will be, create a feature branch for it.

- Make sure the name of your feature branch describes what you're trying to fix. If you don't know what to name it and there's an issue created for it, name your branch `issue-233` (where 233 would be the number of the issue you're fixing).

- If your branch has taken a while to be accepted for merging into master, it's very likely that the `master` branch will have moved forward while you work. In this case, make sure to sync your `master`.
 
```
git checkout master
git pull upstream master
```
   and then rebase your branch to bring it up to speed so it can be merged properly:
```
git checkout my-branch
git rebase master
```
   as you do this you may have to fix any possible conflicts, just follow the instruction git gives you if this is your first time.

- Make sure to `squash` any cosmetic commits into the body of your work so that we don't pollute the history and you don't get more bitcoins than you should from the rest of the collaborators for things like fixing a typo you just introduced on your branch.


# Tip4Commit

You can donate for development, thereby encouraging some developers or you can participate and get a tip for commits we approve of.

[![tip for next commit](https://tip4commit.com/projects/200.svg)](https://tip4commit.com/github/frostwire/frostwire-android)

# Build instructions

## Android Studio

1. Make sure you have [Android Studio](https://developer.android.com/studio/) properly installed.
2. Open the project in Android Studio.
3. Wait for gradle sync and indices to update.
4. Should work out of the box.

Happy coding.

# Download

[Latest binaries](https://www.frostwire.com/android) | [Previous versions (SourceForge)](https://sourceforge.net/projects/frostwire/files/)

**Downloading FrostWire does not constitute permission or a license for obtaining or distributing unauthorized files. It is illegal for you to distribute copyrighted files without permission.**

If you want to know about legal content you can download and distribute legally please visit [FrostClick](http://frostclick.com), [VODO](http://vodo.net), ClearBits.net and [Creative Commons](http://creativecommons.org)

# License

See [COPYING](COPYING) for the license. Frostwire for Android is offered under the [GNU General Public License](http://www.gnu.org/copyleft/gpl.html).
