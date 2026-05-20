# SafeCam Wide Final UI

这是基于已经能运行的 SafeCam Final UI 做的“广角尝试版”。

## 新增

- 优先使用最广角后置镜头
- 本机设置里增加“最广角优先”
- 远程设置页也可以开关“最广角优先”

## 重要说明

这个功能取决于手机系统是否把超广角镜头开放给第三方 CameraX/Camera2。
如果 OPPO / ColorOS 没开放超广角，开启后画面不会变宽，会自动使用默认主摄，不会影响基础监控功能。

## 使用方法

1. 安装 APK
2. 勾选“优先使用最广角后置镜头”
3. 点 START CAMERA SERVER
4. 用另一台手机查看实时画面

如果之后在远程设置页面改了这个开关，需要摄像头手机 STOP 后再 START。

## 远程设置

`http://摄像头手机IP:8080/settings?pin=123456`

## 打包

新建 GitHub 仓库，上传解压后的全部内容。
如果 `.github` 文件夹没传上去，就手动创建：

`.github/workflows/build-apk.yml`

内容复制根目录 `build-apk.yml`。

然后 Actions 运行：

`Build SafeCam Wide Final APK`
