var iTunesApp = WScript.CreateObject("iTunes.Application");

if (iTunesApp == undefined)
{
    // Future proofing: As of Oct. 10th 2019, WScript.CreateObject("iTunes.Application") still works, but perhaps Apple will rename soon
    iTunesApp = WScript.CreateObject("Music.Application");
    if (iTunesApp == undefined)
    {
        WScript.Quit();
    }
}

var args = WScript.Arguments;

var playlistFrostWireName;

playlistFrostWireName = args(0);

var mainLibrary = iTunesApp.LibrarySource;
var playlists = mainLibrary.Playlists;
var numPlaylists = playlists.Count;

var i;
var playlistFrostWire;

playlistFrostWire = null;

for (i = 1; i <= numPlaylists; i++)
{
    var	playlist = playlists.Item(i);
    if (playlist.Name == playlistFrostWireName)
    {
        playlistFrostWire = playlist;
    }
}

if (playlistFrostWire == null)
{
    playlistFrostWire = iTunesApp.CreatePlaylist(playlistFrostWireName);
}

for (i = 1; i < args.length; i++)
{
    playlistFrostWire.AddFile(args(i));
}