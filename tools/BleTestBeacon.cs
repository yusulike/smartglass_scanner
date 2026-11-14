using System;
using System.Collections.Generic;
using System.Text;
using System.Threading;
using Windows.Devices.Bluetooth;
using Windows.Devices.Bluetooth.Advertisement;
using Windows.Devices.Bluetooth.GenericAttributeProfile;
using Windows.Foundation;
using Windows.Storage.Streams;

public static class BleTestBeacon {
  private sealed class Scenario {
    public string Key;
    public string Description;
    public ushort? CompanyId;
    public byte[] ManufacturerPayload;
    public Guid? ServiceUuid;
    public byte? RawSectionType;
    public byte[] RawSectionData;
    public string LocalName;

    public Scenario(
      string key,
      string description,
      ushort? companyId,
      byte[] manufacturerPayload,
      Guid? serviceUuid = null,
      byte? rawSectionType = null,
      byte[] rawSectionData = null,
      string localName = null
    ) {
      Key = key;
      Description = description;
      CompanyId = companyId;
      ManufacturerPayload = manufacturerPayload;
      ServiceUuid = serviceUuid;
      RawSectionType = rawSectionType;
      RawSectionData = rawSectionData;
      LocalName = localName;
    }
  }

  private static readonly Scenario[] Scenarios = new[] {
    new Scenario(
      "1",
      "Ray-Ban Meta (0x058E + META_RB_GLASS) -> HIGH / ALERT",
      0x058E,
      Encoding.ASCII.GetBytes("META_RB_GLASS")
    ),
    new Scenario(
      "2",
      "Quest VR False-Positive Check (0x058E + META_QUEST_3) -> IGNORED",
      0x058E,
      Encoding.ASCII.GetBytes("META_QUEST_3")
    ),
    new Scenario(
      "3",
      "Luxottica Frame (0x0D53) -> HIGH / ALERT",
      0x0D53,
      new byte[] { 0x01, 0x02 }
    ),
    new Scenario(
      "4",
      "Snap Spectacles (0x03C2) -> MEDIUM (2 obs) / ALERT",
      0x03C2,
      new byte[] { 0x01 }
    ),
    new Scenario(
      "5",
      "Meta Platforms General (0x01AB) -> LOW (3 obs) / CAUTION",
      0x01AB,
      new byte[] { 0x01 }
    ),
    new Scenario(
      "6",
      "Zhuhai Jieli Chipset (0x05D6) -> LOW (3 obs) / CAUTION",
      0x05D6,
      new byte[] { 0x01 }
    ),
    new Scenario(
      "7",
      "HeyCyan SDK Service UUID (7905FFF0-B5CE-4E99-A40F-4B1E122D00D0) -> HIGH / ALERT",
      null,
      null,
      Guid.Parse("7905FFF0-B5CE-4E99-A40F-4B1E122D00D0")
    ),
    new Scenario(
      "8",
      "TCL / RayNeo (0x0BC6) -> MEDIUM (2 obs) / ALERT",
      0x0BC6,
      new byte[] { 0x01 }
    ),
    new Scenario(
      "9",
      "Meizu MYVU AR Glasses (0x03AB) -> MEDIUM (2 obs) / DISPLAY_AR",
      0x03AB,
      new byte[] { 0x01 }
    ),
    new Scenario(
      "10",
      "Rokid AI Glasses Service UUID (00009100-0000-1000-8000-00805F9B34FB) -> HIGH / ALERT",
      null,
      null,
      Guid.Parse("00009100-0000-1000-8000-00805F9B34FB")
    ),
    new Scenario(
      "11",
      "Vuzix Smart Glasses (0x060C) -> HIGH / ALERT",
      0x060C,
      new byte[] { 0x01 }
    ),
    new Scenario(
      "12",
      "Da Echo Glasses (0xFEA8 + ServiceData 41 31 49 01 00 00) -> HIGH / ALERT",
      null,
      null,
      null,
      0x16,
      new byte[] { 0xA8, 0xFE, 0x41, 0x31, 0x49, 0x01, 0x00, 0x00 }
    ),
    new Scenario(
      "13",
      "Even Realities G1 (LocalName 'Even G1_TEST') -> MEDIUM (2 obs) / DISPLAY_AR",
      null,
      null,
      null,
      null,
      null,
      "Even G1_TEST"
    ),
    new Scenario(
      "14",
      "HeyCyan Name Prefix (LocalName 'HeyCyan-TEST') -> MEDIUM (2 obs) / CAMERA",
      null,
      null,
      null,
      null,
      null,
      "HeyCyan-TEST"
    )
  };

  private static readonly Dictionary<string, string> Aliases = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase) {
    { "meta_rayban", "1" },
    { "rayban", "1" },
    { "quest", "2" },
    { "meta_quest", "2" },
    { "luxottica", "3" },
    { "snap", "4" },
    { "meta_general", "5" },
    { "jieli", "6" },
    { "heycyan", "7" },
    { "rayneo", "8" },
    { "tcl", "8" },
    { "meizu", "9" },
    { "myvu", "9" },
    { "rokid", "10" },
    { "vuzix", "11" },
    { "da_echo", "12" },
    { "fea8", "12" },
    { "even_g1", "13" },
    { "even", "13" },
    { "heycyan_name", "14" }
  };

  public static int Main(string[] args) {
    string requestedKey = null;
    int durationSec = 0;

    for (int i = 0; i < args.Length; i++) {
      int sec;
      if (args[i] == "--duration" && i + 1 < args.Length && int.TryParse(args[i + 1], out sec)) {
        durationSec = sec;
        i++;
      } else if (!args[i].StartsWith("-")) {
        requestedKey = args[i];
      }
    }

    if (!string.IsNullOrEmpty(requestedKey)) {
      Scenario selected = FindScenario(requestedKey);
      if (selected == null) {
        Console.Error.WriteLine("Unknown scenario: " + requestedKey);
        PrintMenu();
        return 1;
      }
      RunScenario(selected, durationSec);
      return 0;
    }

    while (true) {
      PrintMenu();
      Console.Write("Select scenario (1-14, or q to quit): ");
      string input = Console.ReadLine();
      if (input == null || input.Trim().Equals("q", StringComparison.OrdinalIgnoreCase)) {
        return 0;
      }
      Scenario selected = FindScenario(input.Trim());
      if (selected == null) {
        Console.WriteLine("Invalid selection.");
        continue;
      }
      RunScenario(selected, 0);
    }
  }

  private static Scenario FindScenario(string keyOrAlias) {
    string resolved = Aliases.ContainsKey(keyOrAlias) ? Aliases[keyOrAlias] : keyOrAlias;
    foreach (Scenario s in Scenarios) {
      if (s.Key.Equals(resolved, StringComparison.OrdinalIgnoreCase)) {
        return s;
      }
    }
    return null;
  }

  private static void PrintMenu() {
    Console.WriteLine();
    Console.WriteLine("=== Smart Glasses Detector - Windows BLE Test Beacon ===");
    foreach (Scenario s in Scenarios) {
      Console.WriteLine("  [" + s.Key + "] " + s.Description);
    }
    Console.WriteLine("Aliases: meta_rayban, meta_quest, luxottica, snap, meta_general, jieli, heycyan, rayneo, meizu, rokid, vuzix, da_echo, even_g1, heycyan_name");
  }

  private static void RunScenario(Scenario scenario, int durationSec) {
    Console.WriteLine();
    Console.WriteLine("Starting: " + scenario.Description);

    if (scenario.ServiceUuid.HasValue) {
      RunGattServiceUuidScenario(scenario.ServiceUuid.Value, durationSec);
      return;
    }

    var pub = new BluetoothLEAdvertisementPublisher();
    if (scenario.CompanyId.HasValue) {
      var writer = new DataWriter();
      writer.WriteBytes(scenario.ManufacturerPayload ?? new byte[0]);
      var mfg = new BluetoothLEManufacturerData(scenario.CompanyId.Value, writer.DetachBuffer());
      pub.Advertisement.ManufacturerData.Add(mfg);
    }
    if (scenario.RawSectionType.HasValue && scenario.RawSectionData != null) {
      var writer = new DataWriter();
      writer.WriteBytes(scenario.RawSectionData);
      pub.Advertisement.DataSections.Add(
        new BluetoothLEAdvertisementDataSection(scenario.RawSectionType.Value, writer.DetachBuffer())
      );
    }
    if (!string.IsNullOrEmpty(scenario.LocalName)) {
      pub.Advertisement.LocalName = scenario.LocalName;
    }

    pub.StatusChanged += (sender, eventArgs) => {
      Console.WriteLine("  -> Publisher Status: " + eventArgs.Status + " (Error=" + eventArgs.Error + ")");
    };

    try {
      pub.Start();
      WaitStop(durationSec);
    } catch (Exception ex) {
      if (!string.IsNullOrEmpty(scenario.LocalName) && ex is ArgumentException) {
        Console.Error.WriteLine(
          "Windows WinRT blocked custom LocalName ('" + scenario.LocalName + "'): " +
          "BluetoothLEAdvertisementPublisher disallows overriding AD 0x08/0x09 device names. " +
          "Test name-only rules using nRF Connect on another phone or by renaming the Windows Bluetooth adapter."
        );
      } else {
        Console.Error.WriteLine("Publisher error: " + ex.Message);
      }
    } finally {
      try { pub.Stop(); } catch {}
    }
    Console.WriteLine("Stopped.");
  }

  private static void RunGattServiceUuidScenario(Guid serviceUuid, int durationSec) {
    var op = GattServiceProvider.CreateAsync(serviceUuid);
    while (op.Status == AsyncStatus.Started) {
      Thread.Sleep(50);
    }
    var result = op.GetResults();
    if (result.Error != BluetoothError.Success) {
      Console.Error.WriteLine("GattServiceProvider failed: " + result.Error);
      return;
    }

    var provider = result.ServiceProvider;
    provider.AdvertisementStatusChanged += (sender, eventArgs) => {
      Console.WriteLine("  -> GATT Adv Status: " + eventArgs.Status + " (Error=" + eventArgs.Error + ")");
    };
    provider.StartAdvertising(new GattServiceProviderAdvertisingParameters {
      IsDiscoverable = true,
      IsConnectable = true
    });
    WaitStop(durationSec);
    provider.StopAdvertising();
    Console.WriteLine("Stopped.");
  }

  private static void WaitStop(int durationSec) {
    if (durationSec > 0) {
      Console.WriteLine("Advertising for " + durationSec + "s...");
      Thread.Sleep(durationSec * 1000);
    } else {
      Console.WriteLine("Advertising active. Press [Enter] to stop...");
      Console.ReadLine();
    }
  }
}
