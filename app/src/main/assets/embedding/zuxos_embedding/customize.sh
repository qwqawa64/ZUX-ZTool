#!/system/bin/sh

# Magisk 模块安装脚本
MODDIR=${0%/*}

ui_print "********************************"
ui_print "ZUXOS 平行视界配置模块安装"
ui_print "适配策略移植自模块 [HyperOS完美横屏计划] 作者：酷安@做梦书等"
ui_print "********************************"
ui_print "- 适配应用数量: 1995"
ui_print "- 目标路径: /data/system/zui/embedding/"
ui_print "********************************"

# 创建目标目录
TARGET_DIR="/data/system/zui/embedding"
mkdir -p $TARGET_DIR 2>/dev/null
# 设置目录权限
chmod 0755 $TARGET_DIR 2>/dev/null
chown system:system $TARGET_DIR 2>/dev/null
# 复制配置文件
cp -f $MODDIR/embedding_config.json $TARGET_DIR/embedding_config.json
# 设置文件权限
chmod 0644 $TARGET_DIR/embedding_config.json 2>/dev/null
chown system:system $TARGET_DIR/embedding_config.json 2>/dev/null
ui_print "配置文件已安装到: $TARGET_DIR/embedding_config.json"
ui_print "安装完成！重启后生效。"