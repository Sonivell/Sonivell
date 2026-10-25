# Regras do Sonivell. Media3 e Compose já trazem regras próprias.

# Motor SMART (edição smart): o ONNX Runtime chama classes Java a partir do código nativo.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
