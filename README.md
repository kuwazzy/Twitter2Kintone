# はじめに

本記事では、[AWS Lambda](https://aws.amazon.com/jp/lambda/)と[CData Drivers](https://www.cdata.com/jp/drivers/)を使って、Twitterの特定ハッシュタグのツイート情報をサイボウズ社の[kintone](https://kintone.cybozu.com/jp/)に蓄積するアプリケーションを例に、サーバーレスなFaas(Function as a service)でマルチクラウド間のデータ連携を実現する方法をご紹介します。

# アーキテクチャイメージ
![image.png](https://qiita-image-store.s3.amazonaws.com/0/123181/a77fc8ea-0d17-969d-f487-dbd773828272.png)

# 本方式を活用した利用実績

イベントのハッシュタグをつけてツイートするだけで応募できる抽選会アプリを作成して、これまで下記のIT系のイベントにて、SNSを盛り上げイベントを楽しんでもらう企画として抽選会を実施してきました。

- kintone devCamp 2017 ( https://kintonedevcamp.qloba.com/ )
- 仙台IT文化祭 2017 ( http://2017.sendaiitfes.org/ )
- JJUG CCC 2017 Fall ( http://www.java-users.jp/ccc2017fall/ )　※スポンサーLT枠にて実施

仙台IT文化祭では、2日間で累計約700名が来場したイベントで、景品が超豪華だったということもあり、ハッシュタグ「#sendaiitfes」でなんと**約5400ツイート**を集めました。
イベントブログ記事: ( http://www.cdata.com/jp/blog/News/20171031-sendaiitfes )

kintone上で作成した抽選会アプリ
![image.png](https://qiita-image-store.s3.amazonaws.com/0/123181/2cc60643-09f4-06f2-4d7c-09d7ad2d5f20.png)

# 実現方法

## 各クラウドサービスへの接続

TwitterおよびkintoneはREST APIでのデータ連携インタフェースを持っています。ただし、これらのAPIを通じてデータを連携させるには、それぞれのAPI仕様を理解する必要があります。
- [kintoneのAPI仕様書](https://developer.cybozu.io/hc/ja/articles/201941754)
- [TwitterのAPI仕様書](https://developer.twitter.com/en/docs)
本方式では、各クラウドサービスが公開するAPIを標準化してJDBCの規格でSQLとしてアクセスできる[CData JDBC Drivers製品](http://www.cdata.com/jp/jdbc/)を利用しています。それによりTwitterのツイート情報をSQLのSelect構文で取得して、kintoneにInsert構文でデータを取り込むといった処理をSQLという共通言語で行うことでシンプルなデータ連携Faasとして作成することが出来ます。

## サンプルコード

JavaのプログラムからJDBC経由でTwitterおよびkintoneへアクセスしています。サンプルのJavaコードは以下のGitHubからもダウンロードできます。
[Twitter2Kintone.java](https://github.com/kuwazzy/Twitter2Kintone/blob/master/code/java/Twitter2Kintone.java)

```Twitter2Kintone.java
package cdataj;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

public class Twitter2Kintone{


	public static String UrlKintone = "jdbc:kintone:URL=https://****.cybozu.com;User=****;Password=****;RTK=****;";
	public static String UrlTwitter = "jdbc:twitter:OAuth Access Token Secret=****;OAuth Access Token=****;OAuth Client Id=****;OAuth Client Secret=****;RTK=****;";
	public static String TableNameKintone = "Twitter2Kintone";
	public static String HashtagKintone = "#qiita";

	public static String[] ListMember = { "" };  // Except From_User_Name

	public static void main(String[] args) {
		try {
			long start = System.currentTimeMillis();

			//Connection for kintone
			Class.forName("cdata.jdbc.kintone.KintoneDriver");
			//System.out.println(cdata.jdbc.kintone.KintoneDriver.getRTK());
			Connection connk = DriverManager.getConnection(UrlKintone);
			Statement statk = connk.createStatement();

			//Connection for Twitter
			Class.forName("cdata.jdbc.twitter.TwitterDriver");
			//System.out.println(cdata.jdbc.twitter.TwitterDriver.getRTK());
			Connection connt = DriverManager.getConnection(UrlTwitter);
			Statement statt = connt.createStatement();

			//Select max Twitter ID from kintone
			statk.execute("SELECT max(ID) AS MAX_ID FROM '" + TableNameKintone + "'");
			ResultSet rsk=statk.getResultSet();
			String maxid = "";
			while(rsk.next()){
				maxid = rsk.getString(1);
			}

			//Select Tweets from Twitter
			statt.execute("SELECT ID, CONCAT(FORMAT(Created_At,'yyyy-MM-dd'), 'T', FORMAT(Created_At,'HH:mm:ssZ')) AS Created_At, text, From_User_Id, From_User_Screen_Name, From_User_Name FROM Tweets WHERE SEARCHTERMS = '" + HashtagKintone + "' AND MIN_ID = '" + maxid + "';");
			ResultSet rst=statt.getResultSet();
			String insertstring = "";
			int affectedcount = 0;
			while(rst.next()){
				//Insert into kintone
				String cmd = "INSERT INTO '" + TableNameKintone + "' (ID, Created_At, text, From_User_Id, From_User_Screen_Name, From_User_Name) VALUES (?,?,?,?,?,?);";
				PreparedStatement pstmt = connk.prepareStatement(cmd,Statement.RETURN_GENERATED_KEYS);
				for(int i=1;i<=rst.getMetaData().getColumnCount();i++)
				{
					insertstring = rst.getString(i);
					if (insertstring == null){
						insertstring = "";
					}
					pstmt.setString(i, insertstring);
					System.out.println(rst.getMetaData().getColumnName(i) +"="+insertstring);
				}

				if(Arrays.asList(ListMember).contains(rst.getString(6))){
					//Skip
				}
				else{
					affectedcount += pstmt.executeUpdate();
				}
			}
			System.out.println(affectedcount+" rows are affected");
			long end = System.currentTimeMillis();
			System.out.println((end - start)  + "ms");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

```

コード内の主要パートについて説明します。まず初めに、kintoneとTwitterに接続するためのJDBCの接続URL、および、蓄積するkintoneのアプリ名、Twitterのハッシュタグを設定します。今回は例として「#qiita」をつけてツイートしている情報としました。

```Twitter2Kintone(static).java
	public static String UrlKintone = "jdbc:kintone:URL=https://****.cybozu.com;User=****;Password=****;RTK=****;";
	public static String UrlTwitter = "jdbc:twitter:OAuth Access Token Secret=****;OAuth Access Token=****;OAuth Client Id=****;OAuth Client Secret=****;RTK=****;";
	public static String TableNameKintone = "Twitter2Kintone";
	public static String HashtagKintone = "#qiita";
```

kintoneおよびTwitterへの接続URLの設定例は以下のCData社の製品マニュアルをご覧ください。

- [CData JDBC Driver for kintone 2017J]( http://cdn.cdata.com/help/LKC/jdbc/pg_connectionj.htm )
- [CData JDBC Driver for Twitter 2017J](http://cdn.cdata.com/help/GTC/jdbc/pg_connectingtotwitter.htm)

※Lambdaなどのサーバーレス環境で動作させるにはJDBCのURLにRTK（RunTimeKey）が必要となります。取得方法は[CDataサポート](http://www.cdata.com/jp/support/)よりお問い合わせください。

KintoneDriverとTwitterDriverクラスを呼び出し、それぞれのSaasに接続するコネクションを作成します。

```Twitter2Kintone(connection).java
			//Connection for kintone
			Class.forName("cdata.jdbc.kintone.KintoneDriver");
			//System.out.println(cdata.jdbc.kintone.KintoneDriver.getRTK());
			Connection connk = DriverManager.getConnection(UrlKintone);
			Statement statk = connk.createStatement();

			//Connection for Twitter
			Class.forName("cdata.jdbc.twitter.TwitterDriver");
			//System.out.println(cdata.jdbc.twitter.TwitterDriver.getRTK());
			Connection connt = DriverManager.getConnection(UrlTwitter);
			Statement statt = connt.createStatement();
```

既にkintoneに取り込まれたツイート情報の最大IDを取得します。

```Twitter2Kintone(getmaxid).java
			//Select max Twitter ID from kintone
			statk.execute("SELECT max(ID) AS MAX_ID FROM '" + TableNameKintone + "'");
			ResultSet rsk=statk.getResultSet();
			String maxid = "";
			while(rsk.next()){
				maxid = rsk.getString(1);
			}
```

Twitterから特定のハッシュタグで、既にkintoneに取り込んだID以降のツイートをSelectして、KintoneにInsertします。

```Twitter2Kintone(InsertSelect).java
			//Select Tweets from Twitter
			statt.execute("SELECT ID, CONCAT(FORMAT(Created_At,'yyyy-MM-dd'), 'T', FORMAT(Created_At,'HH:mm:ssZ')) AS Created_At, text, From_User_Id, From_User_Screen_Name, From_User_Name FROM Tweets WHERE SEARCHTERMS = '" + HashtagKintone + "' AND MIN_ID = '" + maxid + "';");
			ResultSet rst=statt.getResultSet();
			String insertstring = "";
			int affectedcount = 0;
			while(rst.next()){
				//Insert into kintone
				String cmd = "INSERT INTO '" + TableNameKintone + "' (ID, Created_At, text, From_User_Id, From_User_Screen_Name, From_User_Name) VALUES (?,?,?,?,?,?);";
				PreparedStatement pstmt = connk.prepareStatement(cmd,Statement.RETURN_GENERATED_KEYS);
				for(int i=1;i<=rst.getMetaData().getColumnCount();i++)
				{
					insertstring = rst.getString(i);
					if (insertstring == null){
						insertstring = "";
					}
					pstmt.setString(i, insertstring);
					System.out.println(rst.getMetaData().getColumnName(i) +"="+insertstring);
				}

				if(Arrays.asList(ListMember).contains(rst.getString(6))){
					//Skip
				}
				else{
					affectedcount += pstmt.executeUpdate();
				}
			}
```

## AWS Lambdaへのデプロイ

AWS Lambdaは、Javaコードおよび、JDBCのJarファイルを含めた形でZIPファイル化してアップロードすることが出来ます。
![image.png](https://qiita-image-store.s3.amazonaws.com/0/123181/987bd463-2201-eb38-0353-f1980b77119f.png)
※ハンドラ名は、Javaコードのパッケージ名.クラス名.（例：cdataj.Twitter2Kintone::main）を指定

アップロードするファイルは、Eclipse等のIDEなどからエクスポートする際に、JDBCのJarを含める形で生成します。
![image.png](https://qiita-image-store.s3.amazonaws.com/0/123181/0680f0a9-89f7-58f0-dc29-b40e6a24f814.png)

## kintoneアプリの準備

ツイート情報を蓄積するためのkintoneアプリを作成します。
![image.png](https://qiita-image-store.s3.amazonaws.com/0/123181/ba241e98-8ea0-dad0-005d-8fa360863bb0.png)

アプリテンプレートをこちらのパスに格納しておりますのでインポートしてご利用ください。

- [Twitter2Kintone.zip](https://github.com/kuwazzy/Twitter2Kintone/blob/master/code/kintone_template/Twitter2Kintone.zip)

本アプリでは、２つのJavascriptライブラリとCSSを利用しています。下記のページよりダウンロードして「JavaScript / CSSでカスタマイズ」に追加してください。

- [sweetalert.min.js](https://js.cybozu.com/sweetalert/v1.1.3/sweetalert.min.js) 
- [sweetalert.css](https://js.cybozu.com/sweetalert/v1.1.3/sweetalert.css)
　※[株式会社ジョイゾー社が作成](https://developer.cybozu.io/hc/ja/articles/204790870-SweetAlert-%E3%82%92%E4%BD%BF%E3%81%A3%E3%81%A6-%E3%83%A1%E3%83%83%E3%82%BB%E3%83%BC%E3%82%B8%E3%82%92%E3%82%B9%E3%82%BF%E3%82%A4%E3%83%AA%E3%83%83%E3%82%B7%E3%83%A5%E3%81%AB%E8%A1%A8%E7%A4%BA%E3%81%95%E3%81%9B%E3%82%88%E3%81%86-)
- [random_lottery.js](https://github.com/kuwazzy/Twitter2Kintone/blob/master/code/js/random_lottery.js)　※サイボウズ社が作成

![image.png](https://qiita-image-store.s3.amazonaws.com/0/123181/319c0820-1dfe-2bb5-cb64-daeaa816c821.png)

## AWS Lambdaでのジョブの実行確認

それでは、AWS Lambda上でジョブのテスト実行を行ってみましょう。作成した関数の左上の「テスト」ボタンをクリックします。しばらくすると、実行結果、および、ログを確認できます。ログの内容を見ると、指定したハッシュタグのツイート情報が出力されていることがわかります。
![image.png](https://qiita-image-store.s3.amazonaws.com/0/123181/a46185e3-9afb-47af-b828-3a67ea3e08f5.png)
あとは、このジョブをスケジュールなどを定義して定期的に実行できるように設定します。本記事ではこの手順は割愛します。

それでは、kintoneのアプリを開いてみましょう。ツイート内容が登録されていれば成功です。
![image.png](https://qiita-image-store.s3.amazonaws.com/0/123181/2cc60643-09f4-06f2-4d7c-09d7ad2d5f20.png)
「抽選タイム！」ボタンをクリックすると、当選者がダイアログとして表示されるようになります。これは、全ツイート内容の中からランダム関数を使って抽出しているのでツイート数が多い人が抽出される確率が上がります。ですので、「ツイートすればするほど当選確率があがる」とイベント企画のなかで事前に言っておくと、SNS上も盛り上がりますね！！


# まとめ

AWS Lambdaで実現するデータ連携FaaS(Function As A Service)の実現手順でした。本記事では、Twitterからkintoneへの連携でしたが、Saas連携のところで利用している[CData JDBC Driver](http://www.cdata.com/jp/jdbc/)は約90を超える様々なデータソースへJDBCなどの規格のもとSQLでアクセスできますので、本方式でサーバーレスアーキテクチャで様々なSaas間連携ができるようになります。
![image.png](https://qiita-image-store.s3.amazonaws.com/0/123181/a79fda49-f81e-cbc0-c03e-cce203cc7460.png)
本手順で使用している[CData JDBC Driver](http://www.cdata.com/jp/jdbc/)は30日間の無償評価版もございますので是非お試してください。
