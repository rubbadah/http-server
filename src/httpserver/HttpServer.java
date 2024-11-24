package httpserver;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpServer {
	// ポート番号
	private static final int PORT = 80;
	// スレッドプールのサイズ
	private static final int THREAD_POOL_SIZE = 10;
	// クッキーに格納する際のキー
	private static String COOKIE_KEY_ACCESS_COUNT = "accessCount";
	// ドキュメント計のルートディレクトリ
	private static String ROOT_PATH = Paths.get(System.getProperty("user.dir"), "src").toString();

	public static void main(String[] args) {
		// サーバー起動
		ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
		try (ServerSocket serverSocket = new ServerSocket(PORT)) {
			System.out.println("http://localhost:" + PORT);

			while (true) {
				Socket clientSocket = serverSocket.accept();
				executorService.submit(() -> {
					try {
						handleRequest(clientSocket);
					} catch (IOException | InterruptedException e) {
						System.err.println(e.getMessage());
					}
				});
			}
		} catch (IOException e) {
			System.err.println(e.getMessage());
		} finally {
			executorService.shutdown();
		}
	}

	/**
	 * リクエストハンドラー
	 * 
	 * @param clientSocket: ソケット
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private static void handleRequest(Socket clientSocket) throws IOException, InterruptedException {
		BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

		String requestLine = in.readLine();
		if (requestLine == null || requestLine.isEmpty()) {
			// リクエストがない場合は返却
			return;
		}

		// リクエストをコンソール出力
		System.out.println("Request: " + requestLine);

		String[] requestParts = requestLine.split(" ");
		if (requestParts.length < 2 || !"GET".equals(requestParts[0])) {
			// リクエストがGETではない場合は405エラーを返す
			String response = generateResponse405();
			out.write(response);
			// ソケットをクローズ
			out.flush();
			clientSocket.close();
			return;
		}

		// パスから対象のhtmlファイルを判定
		String path = requestParts[1];
		// パスが"/"の場合はroot.htmlとする
		String htmlFileName = path.equals("/") ? "root" : path.substring(1);

		int extensionIndex = htmlFileName.lastIndexOf(".");
		if (extensionIndex < 0) {
			// 拡張子が存在しない場合.htmlを付け足す
			htmlFileName += ".html";
		}

		// クッキーの取得
		Map<String, String> cookies = new HashMap<>();
		String line;
		while (!(line = in.readLine()).isEmpty()) {
			if (line.startsWith("Cookie:")) {
				cookies = parseCookies(line);
			}
		}

		// 返却用のコンテンツの一時格納用
		String content = "";

		if (htmlFileName.equals("index.html")) {
			// index時にはアクセスカウンタを画面表示させる

			// クッキーからアクセスカウントを取得
			int accessCount = 0;
			if (cookies.containsKey(COOKIE_KEY_ACCESS_COUNT)) {
				accessCount = Integer.parseInt(cookies.get(COOKIE_KEY_ACCESS_COUNT));
			}
			accessCount++;

			content = generateContentIndex(htmlFileName, accessCount);
		} else {
			// index以外はhtmlファイルの中身をそのまま返す（htmlファイルが存在しない場合はnullが返る）
			content = generateContentDefault(htmlFileName);
		}

		if (content == null) {
			// コンテンツが存在しない場合は404エラー
			String response = generateResponse404();
			out.write(response);
		} else {
			// 1秒のウェイト
			Thread.sleep(1000);

			// レスポンスを成形し、返却
			String response = "HTTP/1.1 200 OK\n" + "Content-Type: text/html; charset=UTF-8\n" + content;
			out.write(response);
		}

		// ソケットをクローズ
		out.flush();
		clientSocket.close();
	}

	/**
	 * クッキーの成形
	 * 
	 * @param cookieHeader: クッキー情報の文字列
	 * @return: Key:ValueでMap化されたクッキー情報
	 */
	private static Map<String, String> parseCookies(String cookieHeader) {
		Map<String, String> cookies = new HashMap<>();

		if (cookieHeader != null && !cookieHeader.isEmpty()) {
			// "Cookie: " の部分を除去して、キーと値のペアを解析
			String[] pairs = cookieHeader.split("[:;]");
			for (String pair : pairs) {
				String[] keyValue = pair.trim().split("=", 2);
				if (keyValue.length == 2) {
					cookies.put(keyValue[0], keyValue[1]);
				}
			}
		}

		return cookies;
	}

	/**
	 * index（アクセスカウンタ表示）画面のコンテンツ生成
	 * 
	 * @param htmlFileName: ファイル名（"index.html"）
	 * @param accessCount:  アクセスカウンタ
	 * @return: クッキー保存用のテキストとHTMLの内容をまとめた文字列
	 */
	private static String generateContentIndex(String htmlFileName, int accessCount) {
		// TODO: 本来ならここはページ単位じゃなくて1本にまとめて、MVCモデルみたいにそれぞれを管理したほうがよさそう（そもそも1ファイルにすべてまとめてるのが良くない）
		try {
			Path filePath = Paths.get(ROOT_PATH, "html", htmlFileName);

			// ファイルが存在するかチェック
			if (!Files.exists(filePath)) {
				return null;
			}

			// 現在日時の取得
			String timeStamp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date());
			// HTMLの内容を取得
			String htmlContent = new String(Files.readAllBytes(filePath), "UTF-8");

			// アクセスカウントとタイムスタンプを挿入
			htmlContent = htmlContent.replace("{{count}}", String.valueOf(accessCount));
			htmlContent = htmlContent.replace("{{timestamp}}", timeStamp);

			// クッキー保存用のテキストとHTMLの内容をまとめて返す
			return String.format("Set-Cookie: %s=%s; Path=/; HttpOnly\n", COOKIE_KEY_ACCESS_COUNT, accessCount) + "\n"
					+ htmlContent;
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * 画面のコンテンツ生成（HTMLの内容そのまま）
	 * 
	 * @param htmlFileName: HTMLファイル名
	 * @return: HTMLの内容の文字列（ファイルが存在しない場合はnull）
	 */
	private static String generateContentDefault(String htmlFileName) {
		try {
			Path filePath = Paths.get(ROOT_PATH, "html", htmlFileName);

			if (!Files.exists(filePath)) {
				// ファイル存在しない場合はnullを返す
				return null;
			}

			// HTMLの内容をそのまま返す
			String htmlContent = new String(Files.readAllBytes(filePath), "UTF-8");
			return "\n" + htmlContent;
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * 404のレスポンス生成
	 * 
	 * @return: 404のレスポンス
	 * @throws IOException
	 */
	private static String generateResponse404() throws IOException {
		// 404の画面は一応べた書きでも用意しておく
		String htmlContent = "<html><body><h1>404 Not Found</h1></body></html>";

		// 404用のhtmlが存在していた場合はそれを使う
		Path filePath = Paths.get(ROOT_PATH, "html", "404.html");
		if (Files.exists(filePath)) {
			htmlContent = new String(Files.readAllBytes(filePath), "UTF-8");
		}

		String response = "HTTP/1.1 404 Not Found\n" + "Content-Type: text/html; charset=UTF-8\n" + "\n" + htmlContent;
		return response;
	}

	/**
	 * 405のレスポンス生成
	 * 
	 * @return: 405のレスポンス
	 * @throws IOException
	 */
	private static String generateResponse405() throws IOException {
		String response = "HTTP/1.1 405 Method Not Allowed\n" + "Content-Type: text/plain; charset=UTF-8\n"
				+ "Allow: GET\n" + "\n" + "405 Method Not Allowed";
		return response;
	}

}
