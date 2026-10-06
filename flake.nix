{
  description = "Z VPN client";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachSystem [ "x86_64-linux" ] (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
        version = "3.1.0";

        zvpn = pkgs.stdenv.mkDerivation {
          pname = "zvpn";
          inherit version;

          src = pkgs.fetchurl {
            url = "https://github.com/TODO-github-repo/releases/download/desktop-v${version}/ZVPN-${version}-linux-x86_64.tar.gz";
            hash = "sha256-IRlPyMx7/zvyRWD+l7bOIG3fmTSFKxTdEZvIe7y2QVo=";
          };

          nativeBuildInputs = with pkgs; [ autoPatchelfHook makeWrapper copyDesktopItems ];

          buildInputs = with pkgs; [
            alsa-lib at-spi2-atk cairo cups.lib dbus.lib expat fontconfig
            freetype gdk-pixbuf glib gtk3 libGL libxkbcommon nss nspr pango
            stdenv.cc.cc.lib xorg.libX11 xorg.libXext xorg.libXi xorg.libXrender
            xorg.libXtst zlib
          ];

          desktopItems = [
            (pkgs.makeDesktopItem {
              name = "zvpn";
              exec = "zvpn";
              icon = "zvpn";
              desktopName = "Z VPN";
              categories = [ "Network" ];
            })
          ];

          installPhase = ''
            runHook preInstall
            mkdir -p $out/share/zvpn $out/bin
            cp -r ./* $out/share/zvpn/

            bin=$(find $out/share/zvpn/bin -maxdepth 1 -type f -perm -u+x | head -1)
            makeWrapper "$bin" $out/bin/zvpn \
              --prefix LD_LIBRARY_PATH : ${pkgs.lib.makeLibraryPath [ pkgs.systemdLibs ]}

            icon=$(find $out/share/zvpn -name '*.png' | head -1)
            if [ -n "$icon" ]; then
              install -Dm444 "$icon" $out/share/icons/hicolor/512x512/apps/zvpn.png
            fi
            runHook postInstall
          '';

          meta = with pkgs.lib; {
            description = "VPN client with Xray, sing-box, Psiphon, Tor and DNS tunnels";
            homepage = "https://example.com/TODO-about-website";
            license = licenses.agpl3Plus;
            platforms = [ "x86_64-linux" ];
            mainProgram = "zvpn";
          };
        };
      in
      {
        packages.default = zvpn;
        packages.zvpn = zvpn;

        apps.default = flake-utils.lib.mkApp { drv = zvpn; };
      });
}
