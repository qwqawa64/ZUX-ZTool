#!/system/bin/sh

MODDIR=${0%/*}

# 模块服务脚本
while true; do
    # 定期检查配置文件是否存在
    if [ ! -f "/data/system/zui/embedding/embedding_config.json" ]; then
        cp -f $MODDIR/embedding_config.json /data/system/zui/embedding/embedding_config.json
        chmod 0644 /data/system/zui/embedding/embedding_config.json
        chown system:system /data/system/zui/embedding/embedding_config.json
    fi
    sleep 60
done
