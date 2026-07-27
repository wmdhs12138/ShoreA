# ShoreA 列表持久化

该版本使用 Preferences DataStore 持久化：

- 列表名称
- 列表标签
- 删除结果
- 撤回删除结果

## 应用

在 ShoreA 仓库根目录执行：

```bash
unzip -o ~/storage/downloads/ShoreA-persistent-lists.zip -d .
./gradlew :app:assembleDebug
```

## 测试

1. 创建两个列表。
2. 给列表添加标签。
3. 完全关闭应用，再重新打开。
4. 确认列表和标签仍存在。
5. 删除列表并等待“撤回”提示消失，再重启确认删除已保存。
6. 删除后点击“撤回”，再重启确认列表已恢复。

## 提交

```bash
git add app/build.gradle.kts app/src/main/java/com/wmdhs/shorea
git commit -m "Persist lists with DataStore"
git push
```
