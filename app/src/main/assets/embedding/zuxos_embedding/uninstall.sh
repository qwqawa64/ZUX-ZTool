#!/system/bin/sh

if ! command -v ui_print >/dev/null 2>&1; then
  ui_print() { echo "$1"; }
fi

ui_print "卸载 ZUXOS 平行视界配置模块..."

# 删除配置文件
rm -f /data/system/zui/embedding/embedding_config.json

ui_print "卸载完成"
