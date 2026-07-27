# ShoreA 应用图标 v2

这一版将前景主体整体缩小，减少启动器遮挡。

在 ShoreA 仓库根目录执行：

```bash
unzip -o ShoreA-app-icon-v2.zip -d .
./gradlew :app:assembleDebug
```

提交：

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res
git commit -m "Adjust ShoreA app icon scale"
git push
```
