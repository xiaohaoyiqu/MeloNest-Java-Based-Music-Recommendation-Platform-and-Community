#!/usr/bin/env python3
                       
\
\
\
\
\
\
   

import sys


def main():
    print(
        "此入口已停用；请在独立训练环境运行 scripts/train_hybrid_model.py",
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    sys.exit(main())
