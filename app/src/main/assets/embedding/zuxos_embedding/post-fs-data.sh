#!/system/bin/sh

MODDIR=${0%/*}

# 确保配置目录存在
TARGET_DIR="/data/system/zui/embedding"
if [ ! -d "$TARGET_DIR" ]; then
    mkdir -p $TARGET_DIR
    chmod 0755 $TARGET_DIR
    chown system:system $TARGET_DIR
fi

# 确保配置文件存在且权限正确
if [ -f "$MODDIR/embedding_config.json" ]; then
    cp -f $MODDIR/embedding_config.json $TARGET_DIR/embedding_config.json
    chmod 0644 $TARGET_DIR/embedding_config.json
    chown system:system $TARGET_DIR/embedding_config.json
fi
