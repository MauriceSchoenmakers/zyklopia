if(typeof void 0===typeof j)var j={module:require('j/module').module};j.module.use_require=true;
j.module('z.server',['j','node=j.http.node','http','net','sys'],function M(){var server=M.z.server,j=M.j,http=M.http,node=M.node,sys=M.sys;

j.log=function(m){M.sys.puts(m);return m;};
j.log('hello'); 

// WEB SERVER
server.data={};

http.createServer(function(K,S){var p=j.f.m.p('p'),s=j.f.m.p('s'),res=j.f.m.p('res'),doc='/home/ubuntu/zyklopia/';return S(
  {m:S.get('z(.*)\\.(.*)'),p:'p$1|s$2',f:j.s(S,S.send_file,[res,doc+'zyklopia/server/web/js/z',p,s])},
  {m:S.get('j(.*)\\.(.*)'),p:'p$1|s$2',f:j.s(S,S.send_file,[res,doc+'j/js/j',p,s])},
  {m:S.get('/test'),f:function(m){S.test.html(m);test(m)}},
  {filter:true,m:S.get('(.*)/'),p:'d$1',f:function(m){m.url.pathname=m.d+'/index.html';}},
  {m:S.get('(.*)\\.(.*)'),p:'p$1|s$2',f:j.s(S,S.send_file,[res,doc+'zyklopia/server/web',p,s])}
 
);}(server.data,node.server)).listen(9000);


function test(m){var log=m.log||j.log;
  var r=node.server.test.request();
  r('GET','/x.html','','html file');
}



// MEDIA SERVER
/*net.createServer(function (socket) {
  socket.setEncoding("utf8");
  socket.write("Echo server\r\n");
  socket.on("data", function (data) {
    j.log('received data');
    socket.write(data);
  });
  socket.on("end", function () {
    socket.end();
  });
}).listen(9100);
*/

});