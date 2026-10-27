# 星空指数通知 (Android)

お天気ナビゲータの星空指数API (`weather-api-prod.n-kishou.co.jp/get-star-index`) を
アプリ内で毎日バックグラウンドチェックし、しきい値以上の地点があれば
端末に通知するAndroidアプリです。

Kotlin + Jetpack Compose + Room + WorkManager + Retrofit で構成しています。

## 機能

- GUIで監視地点を追加・編集・削除（地点名・緯度・経度・標高を手入力）
- 毎日決まった時刻にWorkManagerでバックグラウンド実行し、通知
- しきい値・対象時間帯（例: 20時〜翌2時）・実行時刻を設定画面で変更可能
- 「今すぐ実行」ボタンで動作確認が可能
- 端末再起動後も自動でスケジュール再登録

## 開き方・ビルド方法

1. Android Studio（Koala以降推奨）で、このフォルダ(`HoshizoraNotifierAndroid`)を
   「Open」で開く
2. Gradle Syncが自動的に走ります（初回はライブラリのダウンロードで数分かかります）
3. 実機 or エミュレータ（API 26以上）を接続し、Runボタンでインストール

## 使い方

1. アプリ起動後、右下の「＋」から監視地点を追加
   - 緯度経度は、お天気ナビゲータのサイトで地点を検索した際のAPIレスポンス
     (`pref`/`name`/`lat`/`lon`/`alt`) から取得してください
2. 右上の歯車アイコンから設定画面を開き、しきい値・対象時間帯・実行時刻を調整
3. 通知を確認したい場合は右上の再生アイコン（今すぐ実行）をタップ

## 注意点

- このAPIはサイトのフロントエンドが内部的に呼び出しているもので、
  公式に公開されたAPIではありません。仕様変更で動かなくなる可能性があります。
- Android 12以降は「正確なアラーム」の制限やDoze（低電力モード）の影響で、
  WorkManagerの実行タイミングが数分〜数十分ずれることがあります。
  厳密な時刻指定が必要な用途には向きません。
- `index` の実際の最大値（スケール）は確認できていないため、
  実際の通知結果を見ながら設定画面のしきい値を調整してください。
- 個人利用の範囲で使い、頻繁なアクセスは避けてください（1日1回想定の設計です）。

## 構成

```
HoshizoraNotifierAndroid/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/hoshizora/notifier/
│           ├── HoshizoraApplication.kt   # 起動時にWorkManager登録
│           ├── MainActivity.kt           # Composeナビゲーション
│           ├── data/                     # Room DB・設定(SharedPreferences)
│           ├── network/                  # Retrofit + 星空指数APIモデル
│           ├── worker/                   # 毎日実行されるバックグラウンド処理
│           ├── notification/             # 通知表示
│           └── ui/                       # 地点一覧・追加編集・設定画面
├── build.gradle.kts
└── settings.gradle.kts
```
