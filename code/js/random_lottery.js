/*
* {プログラム名}
* Copyright (c) 作成年 Cybozu
*
* Licensed under the MIT License
*/
(function() {
    "use strict";

    //レコード一覧イベントを取得
    kintone.events.on('app.record.index.show', function(event) {
        if (document.getElementById('my_index_button') !== null) {
            return;
        }
        var myIndexButton = document.createElement('button');
        myIndexButton.id = 'my_index_button';
        myIndexButton.innerHTML = '抽選タイム！🎁';
        myIndexButton.style.fontSize = '25px';
        myIndexButton.style.fontWeight = 'bold';
        myIndexButton.style.backgroundColor = '#F7D40B';
        myIndexButton.style.display = 'inline-block';
        myIndexButton.style.padding = '0.5em 1em';

        myIndexButton.onclick = function(event) {
            
            var offset = 0;
            var records = new Array();
            var loopendflg = false;
          
            while(!loopendflg){
              var query = encodeURIComponent('order by レコード番号 asc limit 500 offset ' + offset);
              var appUrl = kintone.api.url('/k/v1/records') + '?app='+ kintone.app.getId() + '&query=' + query;
          
              // 同期リクエストを行う
              var xmlHttp = new XMLHttpRequest();
              xmlHttp.open("GET", appUrl, false);
              xmlHttp.setRequestHeader('X-Requested-With','XMLHttpRequest');
              xmlHttp.send(null);
          
              //取得したレコードをArrayに格納
              var respdata = JSON.parse(xmlHttp.responseText);
              if(respdata.records.length > 0){
                for(var i = 0; respdata.records.length > i; i++){
                  records.push(respdata.records[i]);
                }
                offset += respdata.records.length;
              }else{
                loopendflg = true;
              }
            }

            var num = records.length
            var rand = Math.floor(Math.random() * num);
            var user = records[rand].From_User_Name.value;
            swal({
                title: user + 'さん当選です！😆',
                text: 'おめでとうございます☆*:.｡. o(≧▽≦)o .｡.:*☆',
                timer: 3000,
                showConfirmButton: false
            });

            var myHeaderSpace = kintone.app.getHeaderSpaceElement();
            var marqueeDiv = document.createElement('marquee');
            marqueeDiv.style.width = '100%';
            marqueeDiv.style.height = '55px';
            marqueeDiv.style.textAlign = 'center';
            marqueeDiv.style.fontSize = '40px';
            marqueeDiv.style.fontWeight = 'bold';
            marqueeDiv.style.backgroundImage = 'radial-gradient(#0C94F5 10%, transparent 20%), radial-gradient(#F5C20C 10%, transparent 20%)';
            marqueeDiv.style.backgroundColor = '#fcfcfc';
            marqueeDiv.style.backgroundSize = '20px 20px';
            marqueeDiv.style.backgroundPosition = '0 0, 10px 10px';
            marqueeDiv.style.marqueeWidth = '300';
            marqueeDiv.scrollamount = '30';
            marqueeDiv.innerHTML = user + 'さん当選です！おめでとうございます☆*:.｡. o(≧▽≦)o .｡.:*☆';
        
            myHeaderSpace.innerHTML = null;
            myHeaderSpace.appendChild(marqueeDiv);
        };
        kintone.app.getHeaderMenuSpaceElement().appendChild(myIndexButton);
    });
})();
