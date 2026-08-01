# 災害支援アプリ（開発版）

このリポジトリは Android 向けの災害支援アプリの開発版サンプルです。主な機能は以下です：

- 定期的な気象チェック（WorkManager）と簡易警報検出 → 緊急通知
- Google Maps 表示（避難所マーカーのサンプル）
- VOICEVOX を使った音声合成（ローカルの VOICEVOX エンジンへ HTTP で接続）
- AppUpdater（外部の version.json を参照して APK をダウンロードし、インストールを誘導）

この README ではローカルで動作確認をするための手順、VOICEVOX の配置方法、version.json の形式例、Maps API キーの取り扱いについて説明します。

---

## 必要条件

- Android Studio（推奨）
- 実機（エミュレータでも動く場合がありますが、VOICEVOX のネイティブ実行などは実機が必要です）
- (任意) Termux 等で VOICEVOX エンジンを起動できる端末環境

---

## 重要なセキュリティ注意

現在、Google Maps API キーが AndroidManifest に埋め込まれています（開発用）。公開リポジトリで平文の API キーを保管すると悪用リスクがあります。運用時は必ずどちらかを行ってください：

1. Google Cloud Console で API キーに Android アプリ制限（パッケージ名 + SHA-1）を設定する。
2. マニフェストに直書きしない。ビルド時に secure gradle property や CI シークレットで差し込む方式に変更する。

---

## VOICEVOX（ローカル音声合成）

このプロジェクトの VoiceVoxManager は、ローカルで稼働する VOICEVOX エンジン（HTTP API: `localhost:50021`）へ接続して `/audio_query` → `/synthesis` を呼び、WAV を生成する仕組みです。セキュリティ上の理由および互換性のため、自動的に GitHub からバイナリをダウンロードする処理は実装していません。

手動でエンジンを用意する手順（例）:

- エミュレータではなく実機（arm64）を使う想定です。

adb を使って端末へバイナリを置く例:

```bash
# ローカルに用意した voicevox_arm64 バイナリを端末へコピー
adb push ./voicevox_arm64 /data/local/tmp/voicevox_arm64
adb shell chmod 755 /data/local/tmp/voicevox_arm64
# 端末上で実行（環境により sudo/run-as が必要／不可）
adb shell "/data/local/tmp/voicevox_arm64 &"
```

Termux を使う場合は、Termux のファイル領域へバイナリを置き、実行して localhost:50021 をリッスンさせてください（端末依存）。

VoiceVoxManager のデフォルト探索場所:

- アプリの filesDir 配下の `voicevox/` フォルダに、ABI に合ったバイナリ名（例: `voicevox_arm64`, `voicevox_armeabi_v7a`, `voicevox_x86_64`, `voicevox_x86`）を置くと、アプリが自動で候補を探して起動を試みます。ファイル名 `voicevox` でも試行します。

- 端末上にバイナリを置けない場合は、LAN 上の VOICEVOX サーバを用意してアプリの「VOICEVOX エンジン設定」でホスト URL を指定してください（例: `http://192.168.1.10:50021`）。

動作確認:

1. VOICEVOX エンジンが起動していることを確認（例: `curl http://127.0.0.1:50021/` が応答する）
2. アプリを起動 → "音声合成（VOICEVOX）" ボタンを押すと合成が行われ、Downloads に WAV が保存され、共有インテントが開きます。

注意: ネイティブバイナリの実行は端末依存（権限や SELinux ポリシー等）です。うまく動かない場合は Termux 上でエンジンを立てる方法を推奨します。

---

## Chromebook / タブレット向けの注意

- ChromeOS の多くは x86_64 アーキテクチャを採用しているため、端末上でネイティブバイナリを実行する場合は `voicevox_x86_64` 等の対応バイナリを用意してください。
- Termux が使える Chromebook では Termux にバイナリを置いて起動する方法が現実的です。
- Chromebook 上でバイナリを実行できない場合は、LAN 上の VOICEVOX サーバを用意してアプリの設定でホスト URL を指定して利用してください。

---

## AppUpdater の使い方（version.json 形式）

AppUpdater は指定した `version.json` を取得し、`versionCode` が現在のアプリより大きければ `apkUrl` から APK をダウンロードしてインストールを誘導します。

`version.json` の例:

```json
{
  "versionCode": 2,
  "versionName": "1.1",
  "apkUrl": "https://example.com/app-1.1.apk"
}
```

注意:
- ダウンロード先のサーバは HTTPS にすること（中間者攻撃対策）。
- Android のインストール設定で「提供元不明のアプリ」を許可する必要があります（デバイスと Android バージョンにより手順が異なります）。

---

## ビルド & 実行（短い手順）

1. Android Studio でプロジェクトを開く。
2. 実機を接続して Run（または Build → Install）。
3. アプリを起動し、ランタイム権限（位置情報、通知など）を許可する。
4. 必要に応じて VOICEVOX エンジンを端末上で起動しておく。
5. MainActivity の各ボタンで機能を確認する（ワーカー登録、地図、合成、アップデート確認）。


### ローカルでのテスト用ビルド

- テスト用に universal APK を生成する（app/build.gradle に `splits.abi.universalApk true` を設定済み）:
  ./gradlew :app:assembleRelease
  生成物: app/build/outputs/apk/release/app-release(-universal).apk

- Play 配布は Android App Bundle (AAB) を推奨:
  ./gradlew :app:bundleRelease

---

## 今後の改善案（参考）

- VOICEVOX の自動ダウンロード・署名検証（ダウンロード時にハッシュ検証）
- オフライン地図タイルのサポート（ダウンロード & タイルオーバーレイ）
- 避難所データのオンライン取得（自治体の公開データ連携）
- UI のアクセシビリティ改善（音声読み上げ、色弱対応など）

---

## 問題が発生したら

Issue を立ててください。できるだけログ（adb logcat）と再現手順を書いていただけると助かります。
