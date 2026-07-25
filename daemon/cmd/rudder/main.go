// Package main 是 Rudder CLI / Daemon 入口。
// 子命令：login、daemon start|stop|status 等（后续任务扩展）。
package main

import (
	"fmt"
	"os"

	"github.com/dawangsky/rudder/daemon/internal/cli"
)

func main() {
	if err := cli.Execute(); err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		os.Exit(1)
	}
}
