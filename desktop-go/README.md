# Sonivell GO

O Sonivell para Linux reescrito em Go. É um protótipo em paralelo ao desktop em Kotlin (`desktop/`),
para medir se um binário nativo, sem JVM, deixa o app mais leve e mais rápido.

| Parte | Como é feita |
|---|---|
| Interface | [Gio](https://gioui.org), desenhada à mão no layout Ondas (fundo Grafite + cor de destaque) |
| Ícones | [Phosphor](https://phosphoricons.com), pesos Regular e Fill, gerados por `tools/gen_phosphor_go.py` |
| Som | ffmpeg decodifica para PCM e o [oto](https://github.com/ebitengine/oto) toca (ALSA/PipeWire) |
| Biblioteca | ffprobe lê as etiquetas; índice em `~/.cache/sonivell-go` |
| Teclas de mídia | MPRIS pelo D-Bus ([godbus](https://github.com/godbus/dbus)) |
| Pacotes | `.deb` (dpkg-deb) e AppImage, por `tools/desktop-go-package.sh` |

Telas: Para você (mixes, álbuns recentes, a seguir), Biblioteca (faixas, álbuns, artistas),
Favoritas, busca, Ajustes (pastas, cor de destaque, sobre), player Vidro, fila e modo compacto.

Ainda não tem: o motor SMART (o porte do código do LatentJam para Go é o próximo passo grande),
os outros estilos visuais e o tema claro.

## Compilar

```sh
sudo apt install golang gcc pkg-config ffmpeg \
  libwayland-dev libx11-dev libx11-xcb-dev libxkbcommon-x11-dev libgles2-mesa-dev \
  libegl1-mesa-dev libffi-dev libxcursor-dev libxfixes-dev libvulkan-dev libasound2-dev
cd desktop-go && go run .
```

Go 1.24 ou mais novo. Para gerar o `.deb` e o AppImage: `tools/desktop-go-package.sh`.
O CI (`.github/workflows/desktop-go.yml`) compila, testa, empacota e tira capturas de tela.

Atalhos: espaço toca/pausa, Ctrl+→ próxima, Ctrl+← anterior.
