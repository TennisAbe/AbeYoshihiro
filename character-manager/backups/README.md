# NOCTALITH 相関図バックアップ

[2026年11月22日のバックアップ](noctalith-relationships-20261122.zip)には、キャラクター42人、画像42枚、関係121件が含まれます。ミカドは41人全員とつながり、ほかの関係は同じ世界の中にあります。

ZIPをダウンロードし、アプリの「バックアップ・復元」画面（`/backup`）から追加復元すると、「相関図」画面（`/relationships`）で確認できます。追加復元では既存のキャラと照合せず、全員が新規登録されます。重複を避けるため、空のDBに一度だけ復元してください。

## 既存のローカルDBに関係だけ追加する

2026年11月22日の42人が登録済みのDBには、[相関図の追加SQL](add-relationships-20261122.sql)を使います。プロフィール・画像・編集履歴・既存の関係を保持し、不足分を追加します。キャラは名前・世界・所属で照合し、対象の不足や重複があると中止します。同じ関係は再実行しても増えません。

1. アプリの `/backup` でバックアップを保存し、アプリを終了します。
2. `main` を取得し、ターミナルで `character-manager` フォルダに移動します。
3. `target/character-manager-0.0.1-SNAPSHOT.jar` が必要です。未ビルドなら、Windows は `mvnw.cmd -DskipTests package`、macOS・Linux は `./mvnw -DskipTests package` を実行します。
4. 以下を実行します。Windows はコマンドプロンプト（cmd.exe）を使ってください。
5. アプリを起動し、「相関図」を開きます。共有されたバックアップ時点のデータから実行すると、42人・121件になります。

```text
java -Dfile.encoding=UTF-8 -Dloader.main=org.h2.tools.RunScript -cp target/character-manager-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher -url "jdbc:h2:file:./data/characters;IFEXISTS=TRUE" -user "" -password "" -script backups/add-relationships-20261122.sql
```
