#!/usr/bin/env python3
                       
                                                                   

import os
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

from train_kaggle_dataset import main


if __name__ == "__main__":
    main()
