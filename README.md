# 簡易 HTTP サーバ

## 概要

インターンの導入として作成。  
HTTP リクエストとレスポンスの基本を学ぶため、簡易 HTTP サーバを実装。  
HTML ファイルの配信機能を備え、ネットワーク通信の仕組みを体験。

## 主な機能

1. **リクエスト受付:** HTTP リクエストを受信し、適切なレスポンスを返却
2. **静的ファイル配信:** 特定の URI に対応する HTML ファイルを配信
3. **エラーハンドリング:** 存在しない URI へのリクエストに対して適切なエラーを返す
4. **アクセスカウンタ:** `index`アクセス時、訪問回数をカウントして表示（cookie を用いてセッション単位で独立してカウント）

## 感想

- 初めて HTTP サーバを実装する中で、リクエストとレスポンスの基本的な流れを理解。
- コードが整理できていない。余裕があれば MVC モデルのような設計パターンを用いて整理されたコードを記載すべきだった。
- フォルダ名・ファイル名等を Github にコミットするに合わせて修正したため、そのままでは動作しない可能性あり。
