var portableSource = WScript.Arguments.Item(0);
var portableTarget = WScript.Arguments.Item(1);

function IsFrostWireRunning() {
    var wmi = GetObject("winmgmts://./root/cimv2");

    var colItems = wmi.ExecQuery("SELECT * FROM Win32_Process");

    var enumItems = new Enumerator(colItems); 

    for (; !enumItems.atEnd(); enumItems.moveNext()) { 
        var item = enumItems.item();
        if (item.Name === "FrostWire.exe") {
            return true;
        }
    }
    
    return false;
}

function WaitFrostWireStopped() {
    for (var i = 0; i < 30; i++) {
        if (!IsFrostWireRunning()) {
            return true;
        }
        WScript.Sleep(1000);
    }
    
    return false;
}

function CopyFrostWireFiles() {
    var fso = WScript.CreateObject("Scripting.FileSystemObject");
    if (fso.FolderExists(portableTarget)) {
        fso.DeleteFolder(portableTarget, true);
    }
    fso.MoveFolder(portableSource, portableTarget);
}

function LaunchFrostWire() {
    var shell = WScript.CreateObject("WScript.Shell")
    shell.Run(portableTarget + "\\FrostWire.exe")
}

if (WaitFrostWireStopped()) {
    CopyFrostWireFiles();
    LaunchFrostWire();
}